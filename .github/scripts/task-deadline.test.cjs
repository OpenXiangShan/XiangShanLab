const {test} = require('node:test');
const assert = require('node:assert/strict');
const timing = require('./task-deadline.cjs');
const assign = require('./task-assignment.cjs');
const remind = require('./ddl-reminder.cjs');
const validate = require('./issue-template-check.cjs');
const calendar = {startHour: 9, endHour: 17, restDates: [], workDates: []};
const body = hours => `### 预估工时\n${hours}\n\n### 补充信息\n无`;
const bot = text => ({user: {login: 'github-actions[bot]', type: 'Bot'}, body: text,
  created_at: '2026-11-12T01:00:00Z'});
const confirmed = record => bot(
  `<!-- task-assignment:resolved claimant=${record.claimant} -->\n${timing.recordMarker(record)}`
);

for (const [name, start, hours, expected] of [
  ['same day', '2026-11-12T09:00:00+08:00', 8, '2026-11-12 17:00'],
  ['weekend', '2026-11-13T16:00:00+08:00', 8, '2026-11-16 16:00'],
  ['fractional', '2026-11-13T16:00:00+08:00', 0.5, '2026-11-13 16:30'],
  ['fractional across weekend', '2026-11-13T16:45:00+08:00', 0.5, '2026-11-16 09:15'],
  ['weekend claim', '2026-11-14T10:00:00+08:00', 8, '2026-11-16 17:00'],
  ['before work', '2026-11-12T07:00:00+08:00', 1, '2026-11-12 10:00'],
  ['after work', '2026-11-12T20:00:00+08:00', 1, '2026-11-13 10:00'],
  ['closing boundary', '2026-11-13T17:00:00+08:00', 1, '2026-11-16 10:00'],
  ['year boundary', '2026-12-31T16:00:00+08:00', 2, '2027-01-01 10:00'],
]) {
  test(name, () => assert.equal(timing.format(timing.addWorkingHours(start, hours, calendar)), expected));
}

test('extra rest day and makeup workday', () => {
  const dates = {...calendar, restDates: ['2026-11-16'], workDates: ['2026-11-14']};
  assert.equal(timing.format(timing.addWorkingHours('2026-11-13T16:00:00+08:00', 16, dates)),
    '2026-11-17 16:00');
  dates.restDates.push('2026-11-14');
  assert.equal(timing.format(timing.addWorkingHours('2026-11-14T09:00:00+08:00', 1, dates)),
    '2026-11-17 10:00');
});

test('hours validation rejects empty, invalid and unbounded estimates', () => {
  for (const value of ['', '0', '-1', 'Infinity', '1e3', '8小时', '2921', '9'.repeat(400), '0.00000000001']) {
    assert.throws(() => timing.estimatedHours(body(value)));
  }
  assert.equal(timing.estimatedHours(body('0.5')), 0.5);
  assert.equal(timing.estimatedHours(body('2920')), 2920);
  assert.throws(() => timing.addWorkingHours('invalid', 8));
  assert.throws(() => timing.addWorkingHours(new Date(), 8, {...calendar, endHour: 18}));
});

test('only trusted confirmed records determine deadlines, not edited hours', () => {
  const record = timing.makeRecord('worker', 8, new Date('2026-11-12T01:00:00Z'));
  const issue = {body: body('100'), assignees: [{login: 'worker'}]};
  const comment = confirmed(record);
  assert.deepEqual(timing.confirmedDeadline(issue, [comment]), record);
  assert.equal(timing.confirmedDeadline({...issue, assignees: []}, [comment]), null);
  assert.equal(timing.confirmedDeadline(issue, [{...comment, user: {login: 'worker'}}]), null);
  assert.equal(timing.readRecord(bot('<!-- task-deadline:{"version":2} -->')), null);
});

test('legacy confirmed tasks retain their existing deadlines', () => {
  const issue = {body: '### 截止时间（DDL）\n2026-11-13 24:00', assignees: [{login: 'worker'}]};
  const comment = bot('<!-- task-assignment:resolved claimant=worker -->');
  assert.equal(timing.format(timing.confirmedDeadline(issue, [comment]).deadline), '2026-11-14 00:00');
  assert.equal(timing.confirmedDeadline(issue, []), null);
  assert.equal(timing.parseLegacy('2026-02-30 10:00'), null);
  const old = {version: 1, claimant: 'worker', workdays: 2,
    claimedAt: '2026-11-12T01:00:00Z', deadline: '2026-11-16T01:00:00Z'};
  assert.deepEqual(timing.readRecord(confirmed(old)), old);
});

test('completion from a previous claim or an untrusted user does not stop reminders', () => {
  const record = timing.makeRecord('worker', 8, new Date('2026-11-12T01:00:00Z'));
  const completion = {user: {login: 'worker'}, body: '/complete', created_at: '2026-11-12T02:00:00Z'};
  assert(timing.isCompleted([completion], record));
  assert(!timing.isCompleted([{...completion, created_at: '2026-11-11T02:00:00Z'}], record));
  assert(!timing.isCompleted([{...completion, created_at: undefined}], record));
  assert(!timing.isCompleted([{...completion, user: {login: 'other'}}], record));
  assert(!timing.isCompleted([{...completion, body: '<!-- task-review:resolved executor=worker -->'}], record));
});

function harness({command = '/confirm', hours = '8', assigned = false, fail = false,
  silent = false, comments, actor = 'publisher'} = {}) {
  const issue = {number: 1, title: '[TASK] test', state: 'open', body: body(hours),
    user: {login: 'publisher'}, assignees: assigned ? [{login: 'worker'}] : []};
  const messages = [];
  const failures = [];
  const history = comments || [bot('<!-- task-assignment:pending claimant=worker -->')];
  let adds = 0;
  const github = {
    paginate: async method => method === github.rest.issues.listForRepo ? [issue] : history,
    rest: {
      issues: {
        get: async () => ({data: issue}),
        addAssignees: async () => {
          adds++;
          if (fail) throw new Error('assignment denied');
          if (!silent) issue.assignees = [{login: 'worker'}];
        },
        createComment: async ({body}) => {messages.push(body);},
        updateComment: async ({body}) => {messages.push(body);},
        listComments() {},
        listForRepo() {},
      },
      repos: {getContent: async () => ({data: [{type: 'dir', name: '6-xiangshan-debug'}]})},
    },
  };
  const context = {repo: {owner: 'owner', repo: 'repo'}, payload: {
    issue, comment: {body: command, user: {login: actor}},
    repository: {default_branch: 'master'},
  }};
  return {github, context, core: {setFailed: x => failures.push(x), warning() {}},
    issue, messages, failures, history, adds: () => adds};
}

test('assignment success writes an immutable hours-based record after assignment', async () => {
  const h = harness();
  const before = Date.now();
  await assign(h);
  assert.equal(h.adds(), 1);
  const record = timing.readRecord(bot(h.messages.at(-1)));
  assert.equal(record.estimatedHours, 8);
  assert(Date.parse(record.claimedAt) >= before);
  assert.equal(record.deadline, timing.addWorkingHours(record.claimedAt, 8).toISOString());
});

for (const options of [{fail: true}, {silent: true}, {hours: '0'}]) {
  test(`failed claim does not start deadline ${JSON.stringify(options)}`, async () => {
    const h = harness(options);
    await assign(h);
    assert(h.messages.length);
    assert(!h.messages.some(x => x.includes('<!-- task-deadline:')));
    if (options.hours) assert.equal(h.adds(), 0);
  });
}

test('pending request, unauthorized confirm and repeated confirm do not start/reset clock', async () => {
  for (const options of [
    {command: '/assign', actor: 'worker', comments: []},
    {actor: 'outsider'},
    {assigned: true, comments: [confirmed(timing.makeRecord('worker', 8))]},
  ]) {
    const h = harness(options);
    await assign(h);
    assert.equal(h.adds(), 0);
    assert(!h.messages.some(x => x.includes('<!-- task-deadline:')));
  }
});

test('template validation accepts hours without DDL and rejects invalid hours', async () => {
  for (const hours of ['8', '0']) {
    const h = harness();
    h.issue.body = `### 任务背景与目标\n目标\n### 所属目录\ndebug\n### 交付清单\n- [ ] 测试\n### 任务难度\n低\n### 预估工时\n${hours}\n### 质押积分\n10`;
    await validate(h);
    assert.equal(h.failures.length, hours === '8' ? 0 : 1);
    assert(!h.messages.at(-1).includes('截止时间（DDL）'));
  }
});

test('reminder uses confirmed record, ignores outsiders, and deduplicates', async () => {
  const record = timing.makeRecord('worker', 8, new Date(Date.now() - 86400000));
  record.deadline = new Date(Date.now() + 3600000).toISOString();
  const outsider = {user: {login: 'outsider'}, body: '/complete', created_at: new Date().toISOString()};
  const h = harness({assigned: true, comments: [confirmed(record), outsider]});
  await remind(h);
  assert.equal(h.messages.length, 1);
  assert(h.messages[0].includes('@worker'));
  h.history.push(bot(h.messages[0]));
  await remind(h);
  assert.equal(h.messages.length, 1);
});

test('no reminder without confirmation, after completion or for a previous claimant', async () => {
  const record = timing.makeRecord('worker', 8, new Date(Date.now() - 86400000));
  record.deadline = new Date(Date.now() + 3600000).toISOString();
  for (const comments of [
    [],
    [confirmed(record), {user: {login: 'worker'}, body: '/complete', created_at: new Date().toISOString()}],
    [confirmed({...record, claimant: 'other'})],
    [confirmed({...record, deadline: new Date(Date.now() - 1000).toISOString()})],
    [confirmed({...record, deadline: new Date(Date.now() + 10800000).toISOString()})],
  ]) {
    const h = harness({assigned: true, comments});
    await remind(h);
    assert.equal(h.messages.length, 0);
  }
});
