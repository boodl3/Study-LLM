package com.studyllm.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cases are real Tesseract output from a scanned discrete-maths lecture PDF. */
class MathSymbolRepairTest {

  @Test
  void restoresUnionAndIntersectionBetweenOperands() {
    assertThat(MathSymbolRepair.repair("(@) AUB=BUA and (b)ANB=BNA."))
        .isEqualTo("(@) A∪B=B∪A and (b)A∩B=B∩A.");
  }

  @Test
  void restoresTheEmptySetThatMadeTheModelStateAWrongIdentity() {
    // The original defect: "AUPp=A" led the chat model to answer "A ∪ U = A" for the identity law.
    assertThat(MathSymbolRepair.repair("(a)AUPp=A and (b)ANU = A."))
        .isEqualTo("(a)A∪∅=A and (b)A∩U=A.");
  }

  @Test
  void tellsTheUnionOperatorApartFromTheUniversalSet() {
    // "AUU=U" is A ∪ U = U — the first U is the operator, the second is the universal set.
    assertThat(MathSymbolRepair.repair("@AUU=U and (b)ANY=0."))
        .isEqualTo("@A∪U=U and (b)A∩∅=∅.");
  }

  @Test
  void restoresComplementFromTheQuoteGlyph() {
    assertThat(MathSymbolRepair.repair("@) AUA“=U and (b)ANA“=4."))
        .isEqualTo("@) A∪Aᶜ=U and (b)A∩Aᶜ=∅.");
    assertThat(MathSymbolRepair.repair("A—B=ANB\".")).isEqualTo("A—B=A∩Bᶜ.");
  }

  @Test
  void leavesProseAlone() {
    String[] prose = {
      "2. Associative Laws: For all sets A, B, and C,",
      "Let all sets referred to below be subsets of a universal set U.",
      "Basic Method for Proving That Sets Are Equal",
      "Given sets A and B, A equals B, written A = B, if, and only if,",
    };
    for (String line : prose) {
      assertThat(MathSymbolRepair.repair(line)).isEqualTo(line);
    }
  }

  @Test
  void repairsTheExpressionOnALineWithoutTouchingTheProseAfterIt() {
    // "U" and "N" also appear as bare words naming the operators in proof justifications, where
    // converting them would be wrong.
    assertThat(MathSymbolRepair.repair("=(ANBYUP by the commutative law for U"))
        .isEqualTo("=(A∩BY∪∅ by the commutative law for U");
    assertThat(MathSymbolRepair.repair("=(ANCYU(BNC) by the commutative law for N"))
        .isEqualTo("=(A∩CY∪(B∩C) by the commutative law for N");
  }

  @Test
  void neverConvertsCBecauseItCollidesWithTheSetNamedC() {
    assertThat(MathSymbolRepair.repair("1. Prove that X C Y.")).isEqualTo("1. Prove that X C Y.");
  }

  @Test
  void leavesLinesWithNothingToRepairByteIdentical() {
    // Guards the spacing side effect: finding an equation must not become an excuse to reformat
    // every "A = B" in the document.
    String line = "Given sets A and B, A equals B, written A = B, if, and only if,";
    assertThat(MathSymbolRepair.repair(line)).isEqualTo(line);
  }

  @Test
  void handlesNullAndEmptyInput() {
    assertThat(MathSymbolRepair.repair(null)).isNull();
    assertThat(MathSymbolRepair.repair("")).isEmpty();
  }
}
