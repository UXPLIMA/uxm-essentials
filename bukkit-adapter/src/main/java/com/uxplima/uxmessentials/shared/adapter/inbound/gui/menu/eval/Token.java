package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

/**
 * One lexical unit of an expression. {@code text} carries the operator/identifier/string spelling; {@code number}
 * is meaningful only for a {@link Kind#NUMBER}. Strings, identifiers, and operators share the {@code text} slot,
 * which keeps the parser's token walking uniform.
 */
record Token(Token.Kind kind, String text, double number) {

    enum Kind {
        NUMBER,
        STRING,
        IDENT,
        OPERATOR,
        LPAREN,
        RPAREN,
        COMMA,
        END
    }

    static Token number(double value) {
        return new Token(Kind.NUMBER, "", value);
    }

    static Token of(Kind kind, String text) {
        return new Token(kind, text, 0);
    }
}
