package com.studyllm.source.extraction;

/** One structural unit of an extracted document — a page, a slide, or the whole file when the
 * format has no such structure (e.g. TXT/MD, where {@code label} is null). */
public record ExtractedSection(String label, String text) {}
