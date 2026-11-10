import {afterEach, describe, expect, it} from "vitest";
import {calendarDateFormat, calendarHourFormat, dateLocale, formatDate, formatDateTime} from "./dates";
import {setLocale} from "./index";

describe("dates", () => {
  afterEach(() => setLocale("fr"));

  it("formats a day as DD/MM/YYYY in French", () => {
    expect(formatDate("2024-01-15", "fr")).toBe("15/01/2024");
    expect(formatDate("2024-01-05T00:00:00Z", "fr")).toBe("05/01/2024");
  });

  it("falls back to ISO 8601 for English, whose region is ambiguous", () => {
    expect(dateLocale("en")).toBeNull();
    expect(formatDate("2024-01-15T00:00:00Z", "en")).toBe("2024-01-15");
  });

  it("keeps the server's calendar day whatever the time zone", () => {
    expect(formatDate("2024-03-01T00:00:00Z", "fr")).toBe("01/03/2024");
  });

  it("follows the current language by default", () => {
    setLocale("en");
    expect(formatDate("2024-01-15")).toBe("2024-01-15");
    expect(calendarDateFormat()).toBe("yy-mm-dd");
    expect(calendarHourFormat()).toBe("24");
    setLocale("fr");
    expect(formatDate("2024-01-15")).toBe("15/01/2024");
    expect(calendarDateFormat()).toBe("dd/mm/yy");
    expect(calendarHourFormat()).toBe("24");
  });

  it("formats an instant with its local time", () => {
    const local = new Date(2024, 0, 15, 9, 5);
    expect(formatDateTime(local, "fr")).toBe("15/01/2024 09:05");
    expect(formatDateTime(local, "en")).toBe("2024-01-15 09:05");
    expect(formatDateTime(new Date(2024, 0, 15, 14, 30), "en")).toBe("2024-01-15 14:30");
  });

  it("returns empty for nothing and the raw text for garbage", () => {
    expect(formatDate(null)).toBe("");
    expect(formatDate("")).toBe("");
    expect(formatDate("n/a")).toBe("n/a");
    expect(formatDateTime(undefined)).toBe("");
  });
});
