function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

// A date without time is a calendar day, not an instant. The server stores "yyyy-MM-dd" as
// midnight UTC and sends it back as an offset date-time ("2024-01-15T00:00:00Z"), so the day is
// read from the value's own date part — converting that instant to local time would show the
// previous day anywhere west of UTC — and written back as a plain "yyyy-MM-dd" built from the
// picked LOCAL day (toISOString() turned local midnight in France into the previous day).
// A date with time is a real instant: parsed as such, written as an ISO string with its offset
// (the server's OffsetDateTime.parse needs one).
export function parseDateAnswer(value: unknown, showTime = false): Date | null {
  if (typeof value !== "string" || !value) return null;
  const day = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (day && (!showTime || value.length === 10)) {
    return new Date(Number(day[1]), Number(day[2]) - 1, Number(day[3]));
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function formatDateAnswer(date: Date, showTime: boolean): string {
  if (showTime) return date.toISOString();
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}
