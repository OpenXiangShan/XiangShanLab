# RISC-V 架构手册 RAG 系统设计方案

> 日期：2026-04-14
> 背景：用户准备面试问题——如何为 RISC-V 架构手册（Privileged + Unprivileged PDF）构建 RAG 系统

---

## 一、系统整体架构

```
【文档侧】                      【查询侧】
PDF 上传                    用户 Query
   │                            │
文档解析 & 预处理              Query 改写（可选）
   │                            │
文本分块 (Chunking)            向量化检索 (Embedding)
   │                            │
生成 Embedding + 索引           向量数据库 (Vector DB)
   │                            │
存储                            混合检索 (Rerank)
                                  │
                          Prompt 组装
                                  │
                            LLM 生成
                                  │
                          返回答案 + 来源引用
```

---

## 二、关键步骤

### 第1步：文档解析与预处理

**工具选型：**

| 工具 | 适用场景 | 特点 |
|------|---------|------|
| pdfplumber | 表格 + 文本提取 | Python 原生，轻量 |
| PyMuPDF (fitz) | 速度快、保留布局 | 适合大批量处理 |
| MinerU | 复杂 PDF（公式/图表/表格）| 效果好，速度慢 |
| marker | PDF → Markdown |效果好，资源消耗大 |

**预处理要点：**
- 提取页码信息（手册 727 + 221 = 948 页）
- 提取章节标题层级（Chapter / Section / Subsection）
- 分离表格（指令格式表、CSR 表格单独处理）
- 清理页眉页脚、统一换行符

### 第2步：文本分块（Chunking）— 核心设计决策

**分块策略对比：**

| 策略 | 适用性 |
|------|--------|
| 固定长度 | ❌ 不推荐，打断语义 |
| 递归字符分割 | ⚠️ 勉强可用 |
| 语义分块 | ✅ 推荐 |
| 结构感知分块 | ✅ 最佳，RISC-V 手册层次分明 |
| 小到大分层检索 | ✅ 高质量场景 |

**推荐参数：**

| 参数 | RISC-V Unprivileged | RISC-V Privileged |
|------|-------------------|------------------|
| 块大小（tokens）| 300-500 | 300-500 |
| 重叠（overlap）| 50-100 | 50-100 |
| 重叠策略 | 按章节边界重叠 | 按章节边界重叠 |
| 表格处理 | 单独分块 | 单独分块 |

**章节标题识别正则：**

```python
CHAPTER_PATTERNS = [
    r"^(\d+)\.\s+([A-Z].*)$",           # 第1章 Introduction
    r"^(\d+\.\d+)\s+([A-Z].*)$",         # 1.1 Section Title
    r"^(\d+\.\d+\.\d+)\s+([A-Z].*)$",    # 1.1.1 Subsection
]
```

### 第3步：Embedding 向量化

**模型选型：**

| 模型 | 维度 | 中文支持 | 推荐场景 |
|------|------|---------|---------|
| **BGE-large-zh-v1.5** | 1024 | ✅ 优秀 | ✅ 生产首选 |
| text-embedding-3-small | 1536 | ✅ 好 | 云端方案 |
| m3e-large | 1024 | ✅ 好 | 轻量部署 |
| bge-m3 | 1024 | ✅ 优秀 | 多语言、混合检索 |
| Jina-embeddings-v3 | 1024 | ✅ 好 | 长文本场景 |

**推荐：BGE-large-zh-v1.5**，原因：
- RISC-V 手册包含大量技术术语（CSR 名称、指令名称）
- 中英文混排内容多，需要精确术语匹配能力

### 第4步：向量数据库选型

| 数据库 | 优点 | 缺点 | 适用场景 |
|--------|------|------|---------|
| **Milvus** | 功能完善，支持混合检索 | 部署复杂 | ✅ 生产环境 |
| **Qdrant** | 性能好，Rust 实现 | 相对年轻 | ✅ 性能敏感 |
| ChromaDB | 轻量、Python 原生 | 功能有限 | ❌ 不推荐生产 |
| FAISS | 速度快，免费 | 无持久化 | ❌ 离线场景 |
| pgvector | PostgreSQL 生态 | 性能一般 | 已有 PG 环境 |
| Pinecone | 托管简单 | 费用高 | 快速验证 |

**索引配置建议：**

```json
{
  "index_type": "HNSW",
  "efConstruction": 256,
  "efSearch": 128,
  "M": 16,
  "metric": "COSINE",
  "quantization": "INT8"
}
```

### 第5步：检索策略

#### 5.1 基础检索
向量检索通过余弦相似度匹配语义相似的文档块。

#### 5.2 混合检索（Hybrid Search）— 推荐方案

**为什么需要混合检索？**
- 向量检索：擅长语义相似（如"中断处理" 匹配 "interrupt handling"）
- 关键词检索（BM25）：擅长精确匹配（如 "mepc" 精确匹配 CSR 名称）
- RISC-V 手册包含大量精确术语，两者结合效果最佳

**Reciprocal Rank Fusion 融合算法：**

```python
def reciprocal_rank_fusion(results_a, results_b, k=60):
    scores = {}
    for rank, item in enumerate(results_a):
        doc_id = item["id"]
        scores[doc_id] = scores.get(doc_id, 0) + vector_weight / (k + rank + 1)
    
    for rank, item in enumerate(results_b):
        doc_id = item["id"]
        scores[doc_id] = scores.get(doc_id, 0) + bm25_weight / (k + rank + 1)
    
    return sorted(scores.keys(), key=lambda x: scores[x], reverse=True)
```

#### 5.3 Query 改写（Query Rewriting）

```python
def query_expansion(query):
    return [
        query,
        translate_to_english(query),
        expand_abbreviations(query),  # "CSR" → "Control and Status Register"
        f"RISC-V 手册中关于 {query} 的说明",
        f"请解释 RISC-V 中 {query} 的工作原理",
    ]
```

### 第6步：重排序（Reranking）

先用向量检索粗筛，再用重排序模型精排（工业级 RAG 标准做法）。

**推荐模型：BGE-Reranker-large**

### 第7步：Prompt 工程

```python
SYSTEM_PROMPT = """你是一个专业的 RISC-V 架构助手，熟悉 RISC-V 指令集架构（特权和非特权架构）。
你可以根据提供的 RISC-V 官方架构手册内容回答用户的问题。

回答规则：
1. 优先使用提供的文档内容回答，如果文档中没有相关信息，明确告知用户
2. 回答时标注参考来源的章节和页码
3. 对于技术细节，引用具体的寄存器名称、指令格式、CSR 地址等信息
4. 回答应该准确、完整、专业
5. 对于模糊或多义的问题，指出不确定性
"""

def build_prompt(query, retrieved_docs):
    context = "\n\n".join([
        f"[来源 {i+1}] {doc['section']} (第 {doc['page_start']} 页):\n{doc['content']}"
        for i, doc in enumerate(retrieved_docs)
    ])
    
    return f"""系统提示：{SYSTEM_PROMPT}

参考文档：
{context}

用户问题：{query}

请根据参考文档回答用户问题。回答时标注来源。
"""
```

### 第8步：LLM 选型

| 模型 | 特点 | 推荐场景 |
|------|------|---------|
| **Qwen2.5-72B-Instruct** | 阿里开源，中文强，开源可部署 | ✅ 本地部署首选 |
| DeepSeek-V3 | 性价比高，效果好 | 成本敏感场景 |
| Claude 3.5 Sonnet | 编程和技术文档能力强 | 云端方案 |
| GPT-4o | 综合能力强 | 云端方案 |

---

## 三、完整 Pipeline 代码

```python
import pdfplumber
from FlagEmbedding import FlagModel
import qdrant_client
from sentence_transformers import CrossEncoder

class RISCVRAGSystem:
    def __init__(self, config):
        self.chunker = RISCVChunker(
            max_chunk_size=config["chunk_size"],
            overlap=config["overlap"]
        )
        self.embedder = FlagModel(config["embedding_model"])
        self.vector_db = qdrant_client.QdrantClient(url=config["qdrant_url"])
        self.reranker = CrossEncoder(config["reranker_model"])
        self.llm = init_llm(config["llm_model"])
    
    def ingest_document(self, pdf_path, doc_type="unprivileged"):
        """文档导入流程"""
        # 1. 解析 PDF
        pages = self._parse_pdf(pdf_path)
        
        # 2. 分块
        chunks = []
        for page in pages:
            page_chunks = self.chunker.chunk_by_structure(
                page["text"], 
                {"doc_type": doc_type, "page": page["page_num"]}
            )
            chunks.extend(page_chunks)
        
        # 3. 向量化
        embeddings = self.embedder.encode(
            [c["content"] for c in chunks],
            batch_size=32,
            normalize=True
        )
        
        # 4. 存储到向量数据库
        self.vector_db.upsert(
            collection_name="riscv_isa",
            points=[{
                "id": chunk["chunk_id"],
                "vector": emb.tolist(),
                "payload": {
                    "content": chunk["content"],
                    "section": chunk["section"],
                    "doc_type": doc_type,
                    "page": chunk["page_start"]
                }
            } for chunk, emb in zip(chunks, embeddings)]
        )
        
        print(f"已导入 {len(chunks)} 个文档块")
    
    def query(self, user_query, top_k=5):
        """查询流程"""
        # 1. 向量检索（粗排）
        query_emb = self.embedder.encode([user_query], normalize=True)
        candidates = self.vector_db.search(
            collection_name="riscv_isa",
            query_vector=query_emb[0].tolist(),
            limit=top_k * 3,
            with_payload=True
        )
        
        # 2. Rerank（精排）
        pairs = [(user_query, c.payload["content"]) for c in candidates]
        rerank_scores = self.reranker.predict(pairs)
        
        ranked = sorted(
            zip(candidates, rerank_scores), 
            key=lambda x: x[1], reverse=True
        )[:top_k]
        
        # 3. 组装 Prompt
        retrieved = [c.payload for c, _ in ranked]
        prompt = self._build_prompt(user_query, retrieved)
        
        # 4. LLM 生成
        response = self.llm.generate(prompt)
        
        return {
            "answer": response,
            "sources": [
                f"第 {r['page']} 页 - {r['section']}"
                for r in retrieved
            ]
        }
```

---

## 四、高级优化策略

### 1. 结构感知检索（Hierarchical Retrieval）

```
第一级：检索到"章节"级别（粗粒度）
  → 快速定位到 Ch30 V Extension

第二级：在该章节内检索"块"级别（细粒度）
  → 精确定位到 30.7.1 Vector Load/Store Instruction Encoding
```

### 2. 迭代检索（Iterative Retrieval）

```python
def iterative_query(query, max_iter=3):
    context = []
    current_query = query
    
    for i in range(max_iter):
        results = hybrid_retriever.retrieve(current_query, top_k=5)
        
        if _is_sufficient(context, results):
            break
        
        partial_answer = llm.generate(
            f"基于以下内容，用一句话总结 '{current_query}' 的答案:\n{results[0]['content']}"
        )
        current_query = f"{query}\n已知信息: {partial_answer}"
        context.extend(results)
    
    return context
```

### 3. 评估体系（RAGAS）

| 指标 | 含义 |
|------|------|
| Faithfulness | 答案是否忠实于上下文 |
| Context Precision | 检索到的上下文与答案的相关度 |
| Answer Relevancy | 答案与问题的相关度 |

### 4. 缓存与性能优化

- 向量检索结果缓存（LRU）
- 文档块 embedding 索引缓存
- 批量预计算热门文档的 embedding

---

## 五、技术选型总结

| 组件 | 推荐方案 | 备选 |
|------|---------|------|
| PDF 解析 | pdfplumber + 自定义后处理 | MinerU |
| 分块策略 | 结构感知分块 | 语义分块 |
| Embedding | **BGE-large-zh-v1.5** | text-embedding-3-small |
| 向量数据库 | **Qdrant / Milvus** | pgvector |
| 全文检索 | BM25 / BGE-M3 混合 | ElasticSearch |
| 重排序 | **BGE-Reranker-large** | Cross-Encoder |
| LLM | Qwen2.5-72B / DeepSeek-V3 | GPT-4o / Claude |
| 评估 | RAGAS | Trulens |

---

## 六、面试回答框架

```
1. 整体架构（画架构图）
   → 文档侧：PDF → 解析 → 分块 → 向量化 → 存储
   → 查询侧：Query → 改写 → 混合检索 → Rerank → LLM → 回答

2. 核心设计决策（重点讲）
   → 分块策略：为什么要按章节边界分块？
   → Embedding 模型选型：为什么选 BGE？
   → 混合检索：为什么需要 BM25 + 向量？

3. 针对 RISC-V 手册的特殊处理
   → CSR 表格单独提取
   → 指令格式表特殊分块
   → 中英混杂术语处理

4. 优化方向
   → 迭代检索
   → 评估体系（RAGAS）
   → 缓存策略

5. 被追问细节
   → 分块大小怎么定？（消融实验）
   → 向量数据库选型依据？（数据规模、QPS）
   → 如何处理表格？（单独用表格检索模型）
```

**核心原则：技术选型要有依据，优化方向要明确，评估体系要完整。**
