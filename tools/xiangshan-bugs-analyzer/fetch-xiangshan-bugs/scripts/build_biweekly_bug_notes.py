#!/usr/bin/env python3
"""Extract bug-fix notes from XiangShan English biweekly posts."""

from __future__ import annotations

import argparse
import html
import json
import re
import time
from html.parser import HTMLParser
from pathlib import Path
from typing import Any
from urllib.parse import urljoin
from urllib.request import Request, urlopen


CATEGORY_URL = "https://docs.xiangshan.cc/zh-cn/latest/blog/category/biweekly-en/"
MODULE_ORDER = ("frontend", "backend", "mem", "cache", "chiselAIA", "chiselIOPMP")
BUG_BUCKET_NAMES = {"bug fixes", "bugfix", "bug fix", "bug"}
BUCKET_NAMES = {
    "rtl features",
    "bug fixes",
    "performance optimizations",
    "timing optimizations",
    "code quality",
    "debugging tools",
    "documentation",
    "others",
    "other",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--category-url", default=CATEGORY_URL)
    parser.add_argument("--out", type=Path, default=Path("kunming-v2-bugs/biweekly-bug-notes.json"))
    parser.add_argument("--sleep", type=float, default=0.05, help="polite delay between page fetches")
    return parser.parse_args()


def fetch(url: str) -> str:
    req = Request(url, headers={"User-Agent": "xiangshan-bugs-analyzer"})
    with urlopen(req, timeout=60) as response:
        return response.read().decode("utf-8", errors="ignore")


def discover_posts(category_url: str, delay: float) -> list[str]:
    first_html = fetch(category_url)
    pages = {category_url}
    for page in re.findall(r'href="([^"]*page/(\d+)/[^"]*)"', first_html):
        pages.add(urljoin(category_url, page[0]))
    # The category exposes non-contiguous page links in the pager. Probe the range up to max.
    max_page = max([1, *[int(match) for _, match in re.findall(r'href="([^"]*page/(\d+)/[^"]*)"', first_html)]])
    for idx in range(2, max_page + 1):
        pages.add(urljoin(category_url, f"page/{idx}/"))

    posts: set[str] = set()
    for page_url in sorted(pages):
        try:
            page_html = first_html if page_url == category_url else fetch(page_url)
        except Exception:
            continue
        for href in re.findall(r'href="([^"]*biweekly-\d+-en/[^"]*)"', page_html):
            posts.add(urljoin(page_url, href))
        time.sleep(delay)
    return sorted(posts, key=post_number, reverse=True)


def post_number(url: str) -> int:
    match = re.search(r"biweekly-(\d+)-en", url)
    return int(match.group(1)) if match else -1


class RecentParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=False)
        self.in_recent = False
        self.in_heading: str | None = None
        self.heading_text = ""
        self.current_section = ""
        self.current_bucket = ""
        self.in_li = False
        self.li_text: list[str] = []
        self.li_links: list[dict[str, str]] = []
        self.current_link: str | None = None
        self.current_link_text: list[str] = []
        self.items: list[dict[str, Any]] = []
        self.title = ""
        self.in_h1 = False
        self.h1_text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = dict(attrs)
        if tag == "h1":
            self.in_h1 = True
            self.h1_text = []
        if tag in {"h2", "h3"}:
            self.in_heading = tag
            self.heading_text = ""
        if self.in_recent and tag == "li":
            self.in_li = True
            self.li_text = []
            self.li_links = []
        if self.in_recent and self.in_li and tag == "a":
            self.current_link = attrs_dict.get("href") or ""
            self.current_link_text = []

    def handle_data(self, data: str) -> None:
        if self.in_h1:
            self.h1_text.append(data)
        if self.in_heading:
            self.heading_text += data
        if self.in_recent and self.in_li:
            self.li_text.append(data)
        if self.current_link is not None:
            self.current_link_text.append(data)

    def handle_entityref(self, name: str) -> None:
        self.handle_data(html.unescape(f"&{name};"))

    def handle_charref(self, name: str) -> None:
        self.handle_data(html.unescape(f"&#{name};"))

    def handle_endtag(self, tag: str) -> None:
        if tag == "h1" and self.in_h1:
            self.title = normalize("".join(self.h1_text))
            self.in_h1 = False
        if tag == "a" and self.current_link is not None:
            self.li_links.append({"url": self.current_link, "text": normalize("".join(self.current_link_text))})
            self.current_link = None
            self.current_link_text = []
        if tag == "li" and self.in_recent:
            text = normalize("".join(self.li_text))
            lowered = text.lower().strip()
            if lowered in BUCKET_NAMES:
                self.current_bucket = lowered
            elif self.current_bucket in BUG_BUCKET_NAMES and self.li_links:
                self.items.append(
                    {
                        "section": self.current_section,
                        "bucket": self.current_bucket,
                        "description": text,
                        "links": self.li_links,
                    }
                )
            self.in_li = False
            self.li_text = []
            self.li_links = []
        if tag in {"h2", "h3"} and self.in_heading == tag:
            heading = normalize(self.heading_text)
            if tag == "h2":
                if heading.lower() == "recent developments":
                    self.in_recent = True
                    self.current_section = ""
                    self.current_bucket = ""
                elif self.in_recent:
                    self.in_recent = False
            elif tag == "h3" and self.in_recent:
                self.current_section = heading
                self.current_bucket = ""
            self.in_heading = None
            self.heading_text = ""


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def section_modules(section: str, description: str) -> list[str]:
    section_lower = section.lower()
    if any(name in section_lower for name in ("xsai", "cute")):
        return []
    text = f"{section}\n{description}".lower()
    modules: list[str] = []
    if "frontend" in text or any(word in text for word in ("ifu", "fetch", "bpu", "ftq", "tage", "btb", "ras")):
        modules.append("frontend")
    if "backend" in text or any(word in text for word in ("csr", "rob", "rename", "interrupt", "trap", "exception", "vset", "vector", "pmp")):
        modules.append("backend")
    if "memblock" in text or any(word in text for word in ("load", "store", "lsq", "sbuffer", "memblock", "mmu", "tlb", "ptw", "pma", "pmp")):
        modules.append("mem")
    if "cache" in text or any(word in text for word in ("dcache", "icache", "l1", "l2", "coupledl2", "xscache", "mshr", "prefetch")):
        modules.append("cache")
    if any(word in text for word in ("chiselaia", "aia", "aplic", "imsic", "vstopi", "hvictl", "siprios", "sireg", "vsei")):
        modules.append("chiselAIA")
    if any(word in text for word in ("chiseliopmp", "iopmp")):
        modules.append("chiselIOPMP")

    if not modules:
        if "frontend" in section_lower:
            modules = ["frontend"]
        elif "backend" in section_lower:
            modules = ["backend"]
        elif "memblock" in section_lower or "cache" in section_lower:
            modules = ["mem", "cache"]
    return [module for module in MODULE_ORDER if module in modules]


def extract_issue_refs(links: list[dict[str, str]]) -> list[dict[str, str]]:
    refs = []
    for link in links:
        url = link["url"]
        match = re.search(r"github\.com/([^/]+/[^/]+)/(?:pull|issues)/(\d+)", url)
        if match:
            refs.append({"repo": match.group(1), "number": match.group(2), "text": link["text"], "url": url})
        elif "github.com" in url:
            refs.append({"repo": "", "number": "", "text": link["text"], "url": url})
    return refs


def parse_post(url: str, delay: float) -> list[dict[str, Any]]:
    page = fetch(url)
    parser = RecentParser()
    parser.feed(page)
    number = post_number(url)
    date_match = re.search(r"/(\d{4})/(\d{2})/(\d{2})/", url)
    date = "-".join(date_match.groups()) if date_match else ""
    notes = []
    for item in parser.items:
        refs = extract_issue_refs(item["links"])
        if not refs:
            continue
        modules = section_modules(item["section"], item["description"])
        if not modules:
            continue
        notes.append(
            {
                "biweekly": number,
                "date": date,
                "source_title": parser.title or f"XiangShan Biweekly {number}",
                "source_url": url,
                "section": item["section"],
                "modules": modules,
                "description": item["description"],
                "refs": refs,
            }
        )
    time.sleep(delay)
    return notes


def main() -> None:
    args = parse_args()
    posts = discover_posts(args.category_url, args.sleep)
    notes: list[dict[str, Any]] = []
    for post in posts:
        notes.extend(parse_post(post, args.sleep))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(notes, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"posts={len(posts)} notes={len(notes)} out={args.out}")


if __name__ == "__main__":
    main()
