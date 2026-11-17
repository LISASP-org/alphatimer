package org.lisasp.alphatiming.cli.analysis;

/** A group of input that could not be parsed: {@code example} is its first occurrence, found in {@code firstFile}. */
public record Finding(FindingKind kind, String detail, long count, String firstFile, String example) {
}
