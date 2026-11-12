const calendar = require('../task-calendar.json');
const DAY = 24 * 60 * 60 * 1000;
const OFFSET = 8 * 60 * 60 * 1000;
const HOURS_LABEL = '预估工时';
const HOUR = 60 * 60 * 1000;
const LEGACY_LABEL = '截止时间（DDL）';
const RECORD = /<!-- task-deadline:(\{[^\r\n]*\}) -->/;

function section(body, label) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return (body || '').match(new RegExp(
    `(?:^|\\n)### ${escaped}\\r?\\n([\\s\\S]*?)(?=\\r?\\n### |$)`
  ))?.[1].trim() || '';
}

function estimatedHours(body) {
  const value = section(body, HOURS_LABEL);
  const hours = Number(value);
  if (!/^\d+(?:\.\d+)?$/.test(value) || !Number.isFinite(hours) ||
      hours <= 0 || hours > 2920 || hours * HOUR < 1) {
    throw new Error(`“${HOURS_LABEL}”必须为大于 0、不超过 2920 的小时数，可使用小数。`);
  }
  return hours;
}

function parseLegacy(value) {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2}) ((?:[01]\d|2[0-3]):[0-5]\d|24:00)$/);
  if (!match) return null;
  const [, year, month, day, hm] = match;
  const days = new Date(Date.UTC(Number(year), Number(month), 0)).getUTCDate();
  if (+month < 1 || +month > 12 || +day < 1 || +day > days) return null;
  const [hour, minute] = hm.split(':').map(Number);
  // Treat legacy 24:00 as midnight of the following day.
  const date = new Date(`${year}-${month}-${day}T00:00:00+08:00`);
  return new Date(date.getTime() + hour * 3600000 + minute * 60000);
}

function isWorkday(localMidnight, dates = calendar) {
  const key = new Date(localMidnight).toISOString().slice(0, 10);
  if (dates.restDates.includes(key)) return false;
  if (dates.workDates.includes(key)) return true;
  return ![0, 6].includes(new Date(localMidnight).getUTCDay());
}

function addWorkingHours(start, hours, dates = calendar) {
  if (!Number.isFinite(new Date(start).getTime()) || !Number.isFinite(hours) ||
      hours <= 0 || hours > 2920 || hours * HOUR < 1) {
    throw new Error('Invalid claim time or estimated hours');
  }
  const {startHour, endHour} = dates;
  if (!Number.isInteger(startHour) || !Number.isInteger(endHour) ||
      startHour < 0 || endHour > 24 || endHour - startHour !== 8) {
    throw new Error('Task calendar must define an eight-hour working window');
  }
  let cursor = new Date(start).getTime() + OFFSET;
  let remaining = Math.ceil(hours * HOUR);
  // Use shifted UTC values for Beijing wall time, counting only the work window.
  while (remaining > 0) {
    const midnight = Math.floor(cursor / DAY) * DAY;
    const next = midnight + DAY;
    const opens = midnight + startHour * HOUR;
    const closes = midnight + endHour * HOUR;
    if (!isWorkday(midnight, dates) || cursor >= closes) {
      cursor = next;
      continue;
    }
    cursor = Math.max(cursor, opens);
    const consumed = Math.min(remaining, closes - cursor);
    cursor += consumed;
    remaining -= consumed;
  }
  return new Date(cursor - OFFSET);
}

function format(date) {
  return new Date(new Date(date).getTime() + OFFSET).toISOString().slice(0, 16).replace('T', ' ');
}

function makeRecord(claimant, hours, now = new Date()) {
  return {
    version: 2,
    claimant,
    estimatedHours: hours,
    hoursPerDay: 8,
    claimedAt: now.toISOString(),
    deadline: addWorkingHours(now, hours).toISOString()
  };
}

function recordMarker(record) {
  return `<!-- task-deadline:${JSON.stringify(record)} -->`;
}

function isBot(comment) {
  return comment.user?.login === 'github-actions[bot]';
}

function readRecord(comment) {
  if (!isBot(comment)) return null;
  const match = (comment.body || '').match(RECORD);
  if (!match) return null;
  try {
    const value = JSON.parse(match[1]);
    const validDuration = value.version === 1
      ? Number.isInteger(value.workdays) && value.workdays >= 1 && value.workdays <= 365
      : value.version === 2 && Number.isFinite(value.estimatedHours) &&
        value.estimatedHours > 0 && value.estimatedHours <= 2920 && value.hoursPerDay === 8;
    if (!validDuration || !/^[A-Za-z0-9-]+$/.test(value.claimant) ||
        !Number.isFinite(Date.parse(value.claimedAt)) ||
        !Number.isFinite(Date.parse(value.deadline)) ||
        Date.parse(value.deadline) <= Date.parse(value.claimedAt)) return null;
    return value;
  } catch {
    return null;
  }
}

function confirmedDeadline(issue, comments) {
  const resolved = [...comments].reverse().find(comment =>
    isBot(comment) && /<!-- task-assignment:resolved claimant=([A-Za-z0-9-]+) -->/.test(comment.body || '')
  );
  if (!resolved) return null;
  const claimant = resolved.body.match(/<!-- task-assignment:resolved claimant=([A-Za-z0-9-]+) -->/)[1];
  if (!issue.assignees.some(a => a.login.toLowerCase() === claimant.toLowerCase())) return null;
  const record = readRecord(resolved);
  if (record && record.claimant.toLowerCase() === claimant.toLowerCase()) return record;
  // Existing confirmed tasks keep their original absolute deadline.
  if (resolved.body.includes('<!-- task-deadline:')) return null;
  const legacy = parseLegacy(section(issue.body, LEGACY_LABEL));
  return legacy ? {claimant, deadline: legacy.toISOString()} : null;
}

function isCompleted(comments, record) {
  const claimTime = Date.parse(record.claimedAt || 0);
  return comments.some(comment => {
    const created = Date.parse(comment.created_at);
    if (!Number.isFinite(created) || created < claimTime) return false;
    if (isBot(comment)) {
      return comment.body?.includes(`<!-- task-review:resolved executor=${record.claimant} -->`);
    }
    return comment.user?.login?.toLowerCase() === record.claimant.toLowerCase() &&
      comment.body?.trim().toLowerCase() === '/complete';
  });
}

module.exports = {
  HOURS_LABEL, LEGACY_LABEL, section, estimatedHours, parseLegacy, addWorkingHours,
  format, makeRecord, recordMarker, readRecord, confirmedDeadline, isCompleted
};
