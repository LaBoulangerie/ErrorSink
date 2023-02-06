package me.wiefferink.errorsink.common;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class EventRuleMatcher {

	// Regex to find named groups inside a regex (the named group-name might be escaped with '\Q'<group>'\E', which this also accounts for)
	private static final Pattern NAMED_GROUPS = Pattern.compile("\\(\\?<(\\\\Q)?(?<group>[a-zA-Z][a-zA-Z0-9]*)(\\\\E)?>");

	private Set<Integer> levelMatches;
	private List<Pattern> messagePatterns;
	private List<Pattern> exceptionPatterns;
	private List<Pattern> threadPatterns;
	private List<Pattern> loggerNamePatterns;

	private ConfigurationNode criteria;
	private Map<String, String> parts;

	public EventRuleMatcher(ConfigurationNode criteria, ConfigurationNode parts) {
		/*if(criteria == null) {
			criteria = new YamlConfiguration();
		}*/
		this.criteria = criteria;
        this.parts = new HashMap<>();
        if (parts != null) {
            for(Object partKey : parts.getChildrenMap().keySet()) {
				String replacement = parts.getNode((String) partKey).getString();
				if(replacement == null || replacement.isEmpty()) {
					continue;
				}

				// Make part available by name of the key
				this.parts.put(
						"{" + partKey + "}",
						"(?<" + Pattern.quote((String) partKey) + ">" + replacement + ")"
				);
			}
		}

		Log.debug("Preparing EventRuleMatcher:", criteria.getPath());

		// Level preparation
		List<String> levels = null;
		try {
			levels = criteria.getNode("matchLevel").getList(TypeToken.of(String.class));
		} catch (ObjectMappingException e) {
			throw new RuntimeException(e);
		}

		if(levels != null) {
			levelMatches = new HashSet<>();
			for (String levelString : levels) {
				Level level = Level.toLevel(levelString, null);
				if (level == null) {
					Log.warn("Incorrect level \"" + levelString + "\" at", criteria.getPath() + ".matchLevel");
				} else {
					levelMatches.add(level.intLevel());
				}
			}
		}
		Log.debug("  levels:", levels, levelMatches);

		// Message matching preparation
		messagePatterns = getRegexPatterns(criteria, "matchMessage");
		Log.debug("  messageRegexes:", messagePatterns);

		// Exception matching preparation
		exceptionPatterns = getRegexPatterns(criteria, "matchException");
		Log.debug("  exceptionRegexes:", exceptionPatterns);

		// Thread pattern preparation
		threadPatterns = getRegexPatterns(criteria, "matchThreadName");
		Log.debug("  threadRegexes:", threadPatterns);

		// Logger pattern preparation
		loggerNamePatterns = getRegexPatterns(criteria, "matchLoggerName");
		Log.debug("  loggerNameRegexes:", loggerNamePatterns);

	}

	/**
	 * Get a list of regexes from the config
	 *
	 * @param section The section to get the regexes from
	 * @param path    The path in the section to try get the regexes form
	 * @return List of compiled regexes if the path has one or a list of strings, otherwise null
	 */
	private List<Pattern> getRegexPatterns(ConfigurationNode section, String... path) {
		List<Pattern> result = null;
		List<String> regexes = null;
		try {
			regexes = section.getNode(path).getList(TypeToken.of(String.class));
		} catch (ObjectMappingException e) {
			throw new RuntimeException(e);
		}

		if(regexes != null && !regexes.isEmpty()) {
			result = new ArrayList<>();
			for(String regex : regexes) {
				// Prepare regex
				for(String partKey : parts.keySet()) {
					regex = regex.replace(partKey, parts.get(partKey));
				}

				// Compile regex
				try {
					result.add(Pattern.compile(regex));
				} catch(PatternSyntaxException e) {
					Log.warn("Incorrect regex: \"" + regex + "\" at", Arrays.toString(criteria.getPath()) + "." + path + ":", ExceptionUtils.getStackTrace(e));
				}
			}
		}
		return result;
	}

	/**
	 * Match a list of patterns to an input
	 *
	 * @param input    The input to check
	 * @param patterns The patterns to match
	 * @return true if one of the patterns matches, otherwise false
	 */
	private boolean matchesAny(String input, List<Pattern> patterns, Map<String, String> replacements) {
		if(input == null || patterns == null) {
			return false;
		}

		for(Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(input);
			if(matcher.find()) {
				// Collect named matcher groups
				Matcher groupMatcher = NAMED_GROUPS.matcher(pattern.pattern());
				while(groupMatcher.find()) {
					try {
						replacements.put(groupMatcher.group("group"), matcher.group(groupMatcher.group("group")));
					} catch(IllegalArgumentException ignored) {
						ignored.printStackTrace();
					}
				}

				// Collect numbered matcher groups
				for(int groupIndex = 1; groupIndex <= matcher.groupCount(); groupIndex++) {
					replacements.put(groupIndex + "", matcher.group(groupIndex));
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Match a rule to an event
	 * @param message     The message to match
	 * @param level       The level to match
	 * @param throwable   The exception to match
	 * @param threadName  The thread name to match
	 * @param loggerName  The logger name to match
	 * @return A map with the captured groups if a match is found, otherwise null
	 */
	public Map<String, String> matches(String message, Level level, Throwable throwable, String threadName, String loggerName) {
		Map<String, String> groups = new HashMap<>();

		// Level match
		if(levelMatches != null && !levelMatches.contains(level.intLevel())) {
			return null;
		}

		// Message match
		if(messagePatterns != null && !matchesAny(message, messagePatterns, groups)) {
			return null;
		}

		// Exception match
		if(exceptionPatterns != null && (throwable == null || !matchesAny(ExceptionUtils.getStackTrace(throwable), exceptionPatterns, groups))) {
			return null;
		}

		// Thread name match
		if(threadPatterns != null && !matchesAny(threadName, threadPatterns, groups)) {
			return null;
		}

		// Logger name match
		if(loggerNamePatterns != null && !matchesAny(loggerName, loggerNamePatterns, groups)) {
			return null;
		}

		return groups;
	}

	@Override
	public String toString() {
		return "EventRuleMatcher(path: " + criteria.getPath() + ")";
	}

}
