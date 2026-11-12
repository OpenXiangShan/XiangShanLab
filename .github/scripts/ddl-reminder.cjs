module.exports = async ({ github, context, core }) => {
const timing = require('./task-deadline.cjs');
const owner = context.repo.owner;
const repo = context.repo.repo;
const reminderWindow = 2 * 60 * 60 * 1000;
const issues = await github.paginate(github.rest.issues.listForRepo, {
  owner,
  repo,
  state: 'open',
  per_page: 100
});

for (const issue of issues) {
  if (
    issue.pull_request ||
    !/^\[TASK\]\s+\S/.test(issue.title) ||
    !issue.assignees.length
  ) {
    continue;
  }

  const comments = await github.paginate(github.rest.issues.listComments, {
    owner,
    repo,
    issue_number: issue.number,
    per_page: 100
  });
  const record = timing.confirmedDeadline(issue, comments);
  if (!record || timing.isCompleted(comments, record)) {
    continue;
  }
  const remaining = Date.parse(record.deadline) - Date.now();
  if (remaining <= 0 || remaining > reminderWindow) continue;
  const ddl = timing.format(record.deadline);
  const marker = `<!-- ddl-reminder:${record.claimedAt || 'legacy'}:${record.deadline} -->`;
  const legacyMarker = `<!-- ddl-reminder:${ddl.replace(' ', 'T')}+08:00 -->`;
  if (comments.some(comment => comment.user?.login === 'github-actions[bot]' &&
      (comment.body?.includes(marker) || comment.body?.includes(legacyMarker)))) {
    continue;
  }

  const recipients = [record.claimant];
  const mentions = recipients.map(login => `@${login}`).join(' ');
  await github.rest.issues.createComment({
    owner,
    repo,
    issue_number: issue.number,
    body: `${marker}\n${mentions}，距离任务 DDL 已不足 2 小时。截止时间：\`${ddl}\`（北京时间 UTC+8），请及时完成交付。`
  });
}

};
