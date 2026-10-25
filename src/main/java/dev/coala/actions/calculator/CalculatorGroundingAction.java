package dev.coala.actions.calculator;

import dev.coala.core.ActionArgumentDefinition;
import dev.coala.core.ActionCall;
import dev.coala.core.ActionResult;
import dev.coala.core.ActionRisk;
import dev.coala.core.AgentContext;
import dev.coala.core.GroundingAction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Set;

/** Deterministic arithmetic grounding action. It never evaluates arbitrary code. */
public final class CalculatorGroundingAction implements GroundingAction {
    @Override public String id() { return "calculate"; }
    @Override public String description() { return "Evaluate a bounded arithmetic expression using +, -, *, /, %, parentheses and decimal numbers."; }
    @Override public Set<String> requiredArguments() { return Set.of("expression"); }
    @Override public ActionRisk risk() { return ActionRisk.LOW; }
    @Override public boolean idempotent() { return true; }
    @Override public List<ActionArgumentDefinition> argumentDefinitions() { return List.of(ActionArgumentDefinition.requiredString("expression", "Arithmetic expression only; max 100 characters")); }
    @Override public void validate(ActionCall call) {
        GroundingAction.super.validate(call);
        if (call.argument("expression", "").length() > 100) throw new IllegalArgumentException("expression must be at most 100 characters");
    }
    @Override public ActionResult execute(ActionCall call, AgentContext context) {
        BigDecimal result = new Parser(call.argument("expression", "")).parse();
        String rendered = result.stripTrailingZeros().toPlainString();
        context.workingMemory().put("calculator.lastExpression", call.argument("expression", ""));
        context.workingMemory().put("calculator.lastResult", rendered);
        return ActionResult.continueWith("Calculation result: " + rendered);
    }

    private static final class Parser {
        private final String input; private int pos;
        Parser(String input) { this.input = input; if (input == null || input.isBlank() || input.length() > 100) throw new IllegalArgumentException("Invalid expression length"); }
        BigDecimal parse() { BigDecimal value = expression(); spaces(); if (pos != input.length()) fail("Unexpected character"); return value; }
        private BigDecimal expression() { BigDecimal value = term(); while (true) { spaces(); if (eat('+')) value = value.add(term(), MathContext.DECIMAL64); else if (eat('-')) value = value.subtract(term(), MathContext.DECIMAL64); else return checked(value); } }
        private BigDecimal term() { BigDecimal value = unary(); while (true) { spaces(); if (eat('*')) value = value.multiply(unary(), MathContext.DECIMAL64); else if (eat('/')) { BigDecimal d = unary(); if (d.compareTo(BigDecimal.ZERO) == 0) fail("Division by zero"); value = value.divide(d, MathContext.DECIMAL64); } else if (eat('%')) { BigDecimal d = unary(); if (d.compareTo(BigDecimal.ZERO) == 0) fail("Modulo by zero"); value = value.remainder(d, MathContext.DECIMAL64); } else return checked(value); } }
        private BigDecimal unary() { spaces(); if (eat('+')) return unary(); if (eat('-')) return unary().negate(); return atom(); }
        private BigDecimal atom() {
            spaces(); if (eat('(')) { BigDecimal v = expression(); spaces(); if (!eat(')')) fail("Missing closing parenthesis"); return v; }
            int start = pos; boolean dot = false, digit = false;
            while (pos < input.length()) { char c = input.charAt(pos); if (Character.isDigit(c)) { digit = true; pos++; } else if (c == '.' && !dot) { dot = true; pos++; } else break; }
            if (!digit) fail("Expected number");
            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) { pos++; if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++; int exponentStart = pos; while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++; if (exponentStart == pos) fail("Invalid exponent"); }
            try { return checked(new BigDecimal(input.substring(start, pos), MathContext.DECIMAL64)); } catch (NumberFormatException ex) { fail("Invalid number"); return BigDecimal.ZERO; }
        }
        private BigDecimal checked(BigDecimal n) { if (n.precision() > 100 || Math.abs(n.scale()) > 100) fail("Result exceeds supported precision"); return n; }
        private void spaces() { while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++; }
        private boolean eat(char c) { if (pos < input.length() && input.charAt(pos) == c) { pos++; return true; } return false; }
        private void fail(String message) { throw new IllegalArgumentException(message + " at position " + pos); }
    }
}
