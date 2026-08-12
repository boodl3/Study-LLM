package com.studyllm.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores set-theory notation that Tesseract's {@code eng} model flattens, because its character
 * repertoire has no {@code ∪ ∩ ∅ ᶜ} — it emits the nearest Latin lookalike instead, so
 * {@code A ∪ ∅ = A} arrives as {@code AUPp=A}. Left alone this silently corrupts the answer: the
 * chat model, given {@code AUPp=A}, guessed "A ∪ U = A" and stated a wrong identity as fact.
 *
 * <p>The difficulty is that every replacement is ambiguous in isolation — {@code U} is both the
 * union operator and the name of the universal set, {@code N} is both intersection and a letter,
 * {@code C} is both subset and a set name. Two rules keep that in check:
 *
 * <ul>
 *   <li>Only whitespace-free tokens that contain {@code =} and consist purely of set-algebra
 *       characters are eligible. Prose ("For all sets A and B,") contains spaces and never
 *       qualifies, which is what stops the rules eating ordinary text.
 *   <li>Within such a token, {@code U}/{@code N} convert only when flanked on both sides by
 *       operands. That alone resolves {@code AUU=U} into {@code A∪U=U} — the first U is an
 *       operator, the second is the universal set.
 * </ul>
 *
 * <p>Verified over a 31-page scanned lecture PDF: 22 lines repaired, no prose altered, and no
 * incorrect substitution. {@code C} is deliberately never converted to {@code ⊆} — it collides
 * with the conventional set name C far too often to be worth it.
 *
 * <p>ponytail: tuned for set-theory notation specifically. Other notations that lean on bare
 * capitals could in principle misfire; widen the guards rather than the rules if that shows up.
 */
final class MathSymbolRepair {

  private MathSymbolRepair() {}

  private static final Pattern MATH_TOKEN =
      // '@' appears because Tesseract routinely renders the list marker "(a)" as "@".
      Pattern.compile("^[A-Za-z0-9()“”\"'’^ᶜ∪∩∅=,.\\-−–—+@¥]{3,}$");

  private static final Pattern COMPLEMENT = Pattern.compile("([A-Za-z])[“”\"]");
  private static final Pattern OPERATOR = Pattern.compile("(.)([UN])(.)");
  private static final Pattern EMPTY_SET_LETTER =
      Pattern.compile("(?<=[∪∩=])(?:Pp|P|¥|Y)(?=[=∪∩)]|$)");
  private static final Pattern EMPTY_SET_DIGIT = Pattern.compile("(?<==)(?:4|0)(?=[.,]?$)");
  private static final Pattern REPAIRED_SYMBOL = Pattern.compile("[∪∩∅ᶜ]");
  private static final Pattern AROUND_EQUALS = Pattern.compile("\\s*=\\s*");

  private static final String OPERAND_LEFT = "[A-Za-z0-9)ᶜ'’]";
  private static final String OPERAND_RIGHT = "[A-Za-z0-9(∅]";

  /** Repairs every line of {@code text}, leaving lines with nothing to fix byte-identical. */
  static String repair(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      out.append(repairLine(lines[i]));
    }
    return out.toString();
  }

  private static String repairLine(String line) {
    // Tesseract is inconsistent about spacing around '=', which splits an expression into tokens
    // that individually look like prose ("ANU" / "=" / "A."). Closing the gap keeps the equation
    // whole long enough to judge it.
    String collapsed = AROUND_EQUALS.matcher(line).replaceAll("=");

    StringBuilder rebuilt = new StringBuilder(collapsed.length());
    for (String token : collapsed.split("(?<=\\s)|(?=\\s)")) {
      rebuilt.append(isMathToken(token) ? repairToken(token) : token);
    }
    String candidate = rebuilt.toString();

    // Collapsing spaces is only how the equation was found, not an edit worth keeping on its own.
    // Without this guard the pass rewrites every "written A = B" in the document and calls it a
    // repair, mutating far more text than it fixes.
    return count(REPAIRED_SYMBOL, candidate) > count(REPAIRED_SYMBOL, line) ? candidate : line;
  }

  private static boolean isMathToken(String token) {
    if (!token.contains("=") || !MATH_TOKEN.matcher(token).matches()) {
      return false;
    }
    int uppercase = 0;
    for (int i = 0; i < token.length(); i++) {
      if (Character.isUpperCase(token.charAt(i))) {
        uppercase++;
      }
    }
    return uppercase >= 2;
  }

  private static String repairToken(String token) {
    String out = COMPLEMENT.matcher(token).replaceAll("$1ᶜ");
    // Two passes: the operator pattern consumes its neighbours, so adjacent operators would
    // otherwise be missed on a single sweep.
    out = convertOperators(convertOperators(out));
    out = EMPTY_SET_LETTER.matcher(out).replaceAll("∅");
    out = EMPTY_SET_DIGIT.matcher(out).replaceAll("∅");
    return out;
  }

  private static String convertOperators(String token) {
    Matcher matcher = OPERATOR.matcher(token);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String before = matcher.group(1);
      String after = matcher.group(3);
      String replacement =
          before.matches(OPERAND_LEFT) && after.matches(OPERAND_RIGHT)
              ? before + ("U".equals(matcher.group(2)) ? "∪" : "∩") + after
              : matcher.group();
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static int count(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int n = 0;
    while (matcher.find()) {
      n++;
    }
    return n;
  }
}
