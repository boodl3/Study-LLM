import { render } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { renderFormattedContent } from "./ChatConversation";

describe("renderFormattedContent", () => {
  test("renders inline math as KaTeX", () => {
    const { container } = render(<>{renderFormattedContent("Area is $x^2 + y^2$ here")}</>);
    expect(container.querySelector(".katex")).not.toBeNull();
  });

  test("renders block math as KaTeX display mode", () => {
    const { container } = render(<>{renderFormattedContent("$$\\frac{1}{(2x+3y)^2}$$")}</>);
    expect(container.querySelector(".katex-display")).not.toBeNull();
  });

  test("renders bold text", () => {
    const { container } = render(<>{renderFormattedContent("**bold** text")}</>);
    expect(container.querySelector("strong")?.textContent).toBe("bold");
  });

  test("leaves plain text untouched", () => {
    const { container } = render(<>{renderFormattedContent("plain text, no markers")}</>);
    expect(container.textContent).toBe("plain text, no markers");
  });
});
