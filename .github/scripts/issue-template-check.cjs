module.exports = async ({ github, context, core }) => {
const timing = require('./task-deadline.cjs');
const issue = context.payload.issue;
const body = issue.body || '';
const errors = [];

if (!/^\[TASK\]\s+\S/.test(issue.title)) {
  errors.push('标题必须以 `[TASK] ` 开头，并在其后填写任务名称。');
}

function section(label) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const pattern = new RegExp(
    `(?:^|\\n)### ${escaped}\\r?\\n([\\s\\S]*?)(?=\\r?\\n### |$)`
  );
  return body.match(pattern)?.[1].trim() || '';
}

function requiredSection(label) {
  const value = section(label);
  if (!value || value === '_No response_') {
    errors.push(`“${label}”不能为空。`);
  }
  return value;
}

requiredSection('任务背景与目标');

const directory = requiredSection('所属目录');
const { data: entries } = await github.rest.repos.getContent({
  ...context.repo,
  path: 'xiangshan-course/docs',
  ref: context.payload.repository.default_branch
});
const directories = entries.filter(entry => entry.type === 'dir')
  .map(entry => entry.name.replace(/^\d+-/, '').replace(/^xiangshan-/, ''));
if (directory && !directories.includes(directory)) {
  errors.push('“所属目录”必须选择有效的课程目录：' + directories.join('、'));
}

const deliverables = requiredSection('交付清单');
if (deliverables && !/^\s*-\s*\[ \]\s+\S/m.test(deliverables)) {
  errors.push('“交付清单”至少需要一个未完成的 Markdown 子项，例如 `- [ ] 交付物`。');
}

const difficulty = requiredSection('任务难度');
if (difficulty && !['低', '中', '高'].includes(difficulty)) {
  errors.push('“任务难度”只能是低、中或高。');
}

const hours = requiredSection(timing.HOURS_LABEL);
if (hours) {
  try {
    timing.estimatedHours(body);
  } catch (error) {
    errors.push(error.message);
  }
}

const stake = requiredSection('质押积分');
if (stake && (!/^\d+$/.test(stake) || Number(stake) <= 0)) {
  errors.push('“质押积分”必须是大于 0 的整数。');
}

const marker = '<!-- task-template-check -->';
const result = errors.length
  ? `${marker}\n## Issue 模板检测未通过\n\n${errors.map(error => `- ${error}`).join('\n')}\n\n请编辑 Issue 修正以上内容，CI 会自动重新检测。`
  : `${marker}\n## Issue 模板检测通过\n\n任务标题和必填字段均符合规范。`;

const comments = await github.paginate(github.rest.issues.listComments, {
  owner: context.repo.owner,
  repo: context.repo.repo,
  issue_number: issue.number,
  per_page: 100
});
const previous = comments.find(comment =>
  comment.user?.type === 'Bot' && comment.body?.includes(marker)
);

if (previous) {
  await github.rest.issues.updateComment({
    owner: context.repo.owner,
    repo: context.repo.repo,
    comment_id: previous.id,
    body: result
  });
} else {
  await github.rest.issues.createComment({
    owner: context.repo.owner,
    repo: context.repo.repo,
    issue_number: issue.number,
    body: result
  });
}

if (errors.length) {
  core.setFailed(errors.join('\n'));
}

};
