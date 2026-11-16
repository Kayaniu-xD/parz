package net.parz.dashboard;

/**
 * Shows a playtime as years, months, days, hours and minutes.
 * A month counts as 30 days and a year as 365 days.
 */
public final class PlaytimeFormat {
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;
	private static final long MONTH = 30 * DAY;
	private static final long YEAR = 365 * DAY;

	private PlaytimeFormat() {
	}

	/**
	 * "1 year 2 months 3 days 4h 12min". Units that are zero at the front are left out:
	 * "45min", "2h 5min", "3 days 4h 12min".
	 */
	public static String format(long ms) {
		ms = Math.max(0, ms);
		long years = ms / YEAR;
		ms %= YEAR;
		long months = ms / MONTH;
		ms %= MONTH;
		long days = ms / DAY;
		ms %= DAY;
		long hours = ms / HOUR;
		long minutes = (ms % HOUR) / MINUTE;

		StringBuilder sb = new StringBuilder();
		if (years > 0) {
			sb.append(years).append(years == 1 ? " year " : " years ");
		}
		if (years > 0 || months > 0) {
			sb.append(months).append(months == 1 ? " month " : " months ");
		}
		if (years > 0 || months > 0 || days > 0) {
			sb.append(days).append(days == 1 ? " day " : " days ");
		}
		if (sb.length() > 0 || hours > 0) {
			sb.append(hours).append("h ");
		}
		sb.append(minutes).append("min");
		return sb.toString();
	}
}
