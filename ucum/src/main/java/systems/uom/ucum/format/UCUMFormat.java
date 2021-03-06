/*
 * Units of Measurement Systems
 * Copyright (c) 2005-2017, Jean-Marie Dautelle, Werner Keil and others.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 *    and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of JSR-363, Units of Measurement nor the names of their contributors may be used to
 *    endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package systems.uom.ucum.format;

import static tech.units.indriya.AbstractUnit.ONE;
import static systems.uom.ucum.format.UCUMConverterFormatter.*;
import si.uom.SI;
import systems.uom.ucum.internal.format.UCUMFormatParser;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.format.AbstractUnitFormat;
import tech.units.indriya.format.SymbolMap;
import tech.units.indriya.function.*;
import tech.units.indriya.internal.format.TokenException;
import tech.units.indriya.internal.format.TokenMgrError;
import tech.units.indriya.unit.AnnotatedUnit;
import tech.units.indriya.unit.MetricPrefix;
import tech.units.indriya.unit.TransformedUnit;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.UnitConverter;
import javax.measure.format.ParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.ParsePosition;
import java.util.*;

/**
 * <p>
 * This class provides the interface for formatting and parsing {@link AbstractUnit units} according to the
 * <a href="http://unitsofmeasure.org/">Uniform Code for CommonUnits of Measure</a> (UCUM).
 * </p>
 *
 * <p>
 * For a technical/historical overview of this format please read <a href="http://www.pubmedcentral.nih.gov/articlerender.fcgi?artid=61354">
 * CommonUnits of Measure in Clinical Information Systems</a>.
 * </p>
 *
 * <p>
 * As of revision 1.16, the BNF in the UCUM standard contains an <a href="http://unitsofmeasure.org/ticket/4">error</a>. I've attempted to work around
 * the problem by modifying the BNF productions for &lt;Term&gt;. Once the error in the standard is corrected, it may be necessary to modify the
 * productions in the UCUMFormatParser.jj file to conform to the standard.
 * </p>
 *
 * @author <a href="mailto:eric-r@northwestern.edu">Eric Russell</a>
 * @author <a href="mailto:units@catmedia.us">Werner Keil</a>
 * @version 0.8, 6 October 2018
 */
public abstract class UCUMFormat extends AbstractUnitFormat {
    /**
     * 
     */
    // private static final long serialVersionUID = 8586656823290135155L;

    // A helper to declare bundle names for all instances
    private static final String BUNDLE_BASE = UCUMFormat.class.getName();

    // /////////////////
    // Class methods //
    // /////////////////

    /**
     * Returns the instance for formatting/parsing using the given variant
     * 
     * @param variant
     *            the <strong>UCUM</strong> variant to use
     * @return a {@link UCUMFormat} instance
     */
    public static UCUMFormat getInstance(Variant variant) {
        switch (variant) {
            case CASE_INSENSITIVE:
                return Parsing.DEFAULT_CI;
            case CASE_SENSITIVE:
                return Parsing.DEFAULT_CS;
            case PRINT:
                return Print.DEFAULT;
            default:
                throw new IllegalArgumentException("Unknown variant: " + variant);
        }
    }

    /**
     * Returns an instance for formatting and parsing using user defined symbols
     * 
     * @param variant
     *            the <strong>UCUM</strong> variant to use
     * @param symbolMap
     *            the map of user defined symbols to use
     * @return a {@link UCUMFormat} instance
     */
    public static UCUMFormat getInstance(Variant variant, SymbolMap symbolMap) {
        switch (variant) {
            case CASE_INSENSITIVE:
                return new Parsing(symbolMap, false);
            case CASE_SENSITIVE:
                return new Parsing(symbolMap, true);
            case PRINT:
                return new Print(symbolMap);
            default:
                throw new IllegalArgumentException("Unknown variant: " + variant);
        }
    }

    /**
     * The symbol map used by this instance to map between {@link AbstractUnit Unit}s and <code>String</code>s.
     */
    final SymbolMap symbolMap;

    /**
     * Get the symbol map used by this instance to map between {@link AbstractUnit Unit}s and <code>String</code>s, etc...
     * 
     * @return SymbolMap the current symbol map
     */
    @Override
    protected SymbolMap getSymbols() {
        return symbolMap;
    }

    //////////////////
    // Constructors //
    //////////////////
    /**
     * Base constructor.
     */
    UCUMFormat(SymbolMap symbolMap) {
        this.symbolMap = symbolMap;
    }

    // ///////////
    // Parsing //
    // ///////////
    public abstract Unit<? extends Quantity<?>> parse(CharSequence csq, ParsePosition cursor) throws ParserException;

    protected Unit<?> parse(CharSequence csq, int index) throws ParserException {
        return parse(csq, new ParsePosition(index));
    }

    @Override
    public abstract Unit<? extends Quantity<?>> parse(CharSequence csq) throws ParserException;

    ////////////////
    // Formatting //
    ////////////////
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Appendable format(Unit<?> unknownUnit, Appendable appendable) throws IOException {
        if (!(unknownUnit instanceof AbstractUnit)) {
            throw new UnsupportedOperationException("The UCUM format supports only known units (AbstractUnit instances)");
        }
        AbstractUnit unit = (AbstractUnit) unknownUnit;
        CharSequence symbol;
        CharSequence annotation = null;
        if (unit instanceof AnnotatedUnit) {
            AnnotatedUnit annotatedUnit = (AnnotatedUnit) unit;
            unit = annotatedUnit.getActualUnit();
            annotation = annotatedUnit.getAnnotation();
        }
        String mapSymbol = symbolMap.getSymbol(unit);
        if (mapSymbol != null) {
            symbol = mapSymbol;
        } else if (unit instanceof TransformedUnit) {
            final StringBuilder temp = new StringBuilder();
            final Unit<?> parentUnit = ((TransformedUnit) unit).getParentUnit();
            final UnitConverter converter = unit.getConverterTo(parentUnit);
            final boolean printSeparator = !parentUnit.equals(ONE);

            format(parentUnit, temp);
            formatConverter(converter, printSeparator, temp, symbolMap);

            symbol = temp;
        } else if (unit.getBaseUnits() != null) {
            Map<? extends AbstractUnit<?>, Integer> productUnits = unit.getBaseUnits();
            StringBuffer app = new StringBuffer();
            for (AbstractUnit<?> u : productUnits.keySet()) {
                StringBuffer temp = new StringBuffer();
                temp = (StringBuffer) format(u, temp);
                if ((temp.indexOf(".") >= 0) || (temp.indexOf("/") >= 0)) {
                    temp.insert(0, '(');
                    temp.append(')');
                }
                int pow = productUnits.get(u);
                int indexToAppend;
                if (app.length() > 0) { // Not the first unit.

                    if (pow >= 0) {

                        if (app.indexOf("1/") >= 0) {
                            indexToAppend = app.indexOf("1/");
                            app.replace(indexToAppend, indexToAppend + 2, "/");
                            // this statement make sure that (1/y).x will be
                            // (x/y)

                        } else if (app.indexOf("/") >= 0) {
                            indexToAppend = app.indexOf("/");
                            app.insert(indexToAppend, ".");
                            indexToAppend++;
                            // this statement make sure that (x/z).y will be
                            // (x.y/z)

                        } else {
                            app.append('.');
                            indexToAppend = app.length();
                            // this statement make sure that (x).y will be (x.y)
                        }

                    } else {
                        app.append('/');
                        pow = -pow;

                        indexToAppend = app.length();
                        // this statement make sure that (x).y^-z will be
                        // (x/y^z), where z would be added if it has a value
                        // different than 1.
                    }

                } else { // First unit.
                    if (pow < 0) {
                        app.append("1/");
                        pow = -pow;
                        // this statement make sure that x^-y will be (1/x^y),
                        // where z would be added if it has a value different
                        // than 1.
                    }
                    indexToAppend = app.length();
                }

                app.insert(indexToAppend, temp);

                if (pow != 1) {
                    app.append(Integer.toString(pow));
                    // this statement make sure that the power will be added if
                    // it's different than 1.
                }
            }
            symbol = app;
        } else if (!unit.isSystemUnit() || unit.equals(SI.KILOGRAM)) {
            final StringBuilder temp = new StringBuilder();
            UnitConverter converter;
            boolean printSeparator;
            if (unit.equals(SI.KILOGRAM)) {
                // A special case because KILOGRAM is a BaseUnit instead of
                // a transformed unit, for compatibility with existing SI
                // unit system.
                format(SI.GRAM, temp);
                converter = PowersOfIntConverter.of(MetricPrefix.KILO);
                printSeparator = true;
            } else {
                Unit<?> parentUnit = unit.getSystemUnit();
                converter = unit.getConverterTo(parentUnit);
                if (parentUnit.equals(SI.KILOGRAM)) {
                    // More special-case hackery to work around gram/kilogram
                    // inconsistency
                    parentUnit = SI.GRAM;
                    converter = converter.concatenate(PowersOfIntConverter.of(MetricPrefix.KILO));
                }
                format(parentUnit, temp);
                printSeparator = !parentUnit.equals(ONE);
            }
            formatConverter(converter, printSeparator, temp, symbolMap);
            symbol = temp;
        } else if (unit.getSymbol() != null) {
            symbol = unit.getSymbol();
        } else {
            throw new IllegalArgumentException("Cannot format the given Object as UCUM units (unsupported unit " + unit.getClass().getName() + "). "
                    + "Custom units types should override the toString() method as the default implementation uses the UCUM format.");
        }

        appendable.append(symbol);
        if (annotation != null && annotation.length() > 0) {
            appendAnnotation(symbol, annotation, appendable);
        }

        return appendable;
    }

    public void label(Unit<?> unit, String label) {
        throw new UnsupportedOperationException("label() not supported by this implementation");
    }

    public boolean isLocaleSensitive() {
        return false;
    }

    void appendAnnotation(CharSequence symbol, CharSequence annotation, Appendable appendable) throws IOException {
        appendable.append('{');
        appendable.append(annotation);
        appendable.append('}');
    }

    // static final ResourceBundle.Control getControl(final String key) {
    // return new ResourceBundle.Control() {
    // @Override
    // public List<Locale> getCandidateLocales(String baseName, Locale locale) {
    // if (baseName == null)
    // throw new NullPointerException();
    // if (locale.equals(new Locale(key))) {
    // return Arrays.asList(
    // locale,
    // Locale.GERMANY,
    // // no Locale.GERMAN here
    // Locale.ROOT);
    // } else if (locale.equals(Locale.GERMANY)) {
    // return Arrays.asList(
    // locale,
    // // no Locale.GERMAN here
    // Locale.ROOT);
    // }
    // return super.getCandidateLocales(baseName, locale);
    // }
    // };
    // }

    // /////////////////
    // Inner classes //
    // /////////////////

    /**
     * Variant of unit representation in the UCUM standard
     * 
     * @see <a href= "http://unitsofmeasure.org/ucum.html#section-Character-Set-and-Lexical-Rules"> UCUM - Character Set and Lexical Rules</a>
     */
    public static enum Variant {
        CASE_SENSITIVE, CASE_INSENSITIVE, PRINT
    }

    /**
     * The Print format is used to output units according to the "print" column in the UCUM standard. Because "print" symbols in UCUM are not unique,
     * this class of UCUMFormat may not be used for parsing, only for formatting.
     */
    private static final class Print extends UCUMFormat {

        /**
         *
         */
        // private static final long serialVersionUID = 2990875526976721414L;
        private static final SymbolMap PRINT_SYMBOLS = SymbolMap.of(ResourceBundle.getBundle(BUNDLE_BASE + "_Print"));
        private static final Print DEFAULT = new Print(PRINT_SYMBOLS);

        public Print(SymbolMap symbols) {
            super(symbols);
        }

        @Override
        public Unit<? extends Quantity<?>> parse(CharSequence csq, ParsePosition pos) throws IllegalArgumentException {
            throw new UnsupportedOperationException("The print format is for pretty-printing of units only. Parsing is not supported.");
        }

        @Override
        void appendAnnotation(CharSequence symbol, CharSequence annotation, Appendable appendable) throws IOException {
            if (symbol != null && symbol.length() > 0) {
                appendable.append('(');
                appendable.append(annotation);
                appendable.append(')');
            } else {
                appendable.append(annotation);
            }
        }

        @Override
        public Unit<? extends Quantity<?>> parse(CharSequence csq) throws IllegalArgumentException {
            return parse(csq, new ParsePosition(0));

        }
    }

    /**
     * The Parsing format outputs formats and parses units according to the "c/s" or "c/i" column in the UCUM standard, depending on which SymbolMap
     * is passed to its constructor.
     */
    private static final class Parsing extends UCUMFormat {
        // private static final long serialVersionUID = -922531801940132715L;
        private static final SymbolMap CASE_SENSITIVE_SYMBOLS = SymbolMap
                .of(ResourceBundle.getBundle(BUNDLE_BASE + "_CS", new ResourceBundle.Control() {
                    @Override
                    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
                        if (baseName == null)
                            throw new NullPointerException();
                        if (locale.equals(new Locale("", "CS"))) {
                            return Arrays.asList(locale, Locale.ROOT);
                        }
                        return super.getCandidateLocales(baseName, locale);
                    }
                }));
        private static final SymbolMap CASE_INSENSITIVE_SYMBOLS = SymbolMap
                .of(ResourceBundle.getBundle(BUNDLE_BASE + "_CI", new ResourceBundle.Control() {
                    @Override
                    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
                        if (baseName == null)
                            throw new NullPointerException();
                        if (locale.equals(new Locale("", "CI"))) {
                            return Arrays.asList(locale, Locale.ROOT);
                        } else if (locale.equals(Locale.GERMANY)) { // TODO
                            // why
                            // GERMANY?
                            return Arrays.asList(locale,
                                    // no Locale.GERMAN here
                                    Locale.ROOT);
                        }
                        return super.getCandidateLocales(baseName, locale);
                    }
                }));
        private static final Parsing DEFAULT_CS = new Parsing(CASE_SENSITIVE_SYMBOLS, true);
        private static final Parsing DEFAULT_CI = new Parsing(CASE_INSENSITIVE_SYMBOLS, false);
        private final boolean caseSensitive;

        public Parsing(SymbolMap symbols, boolean caseSensitive) {
            super(symbols);
            this.caseSensitive = caseSensitive;
        }

        @Override
        public Unit<? extends Quantity<?>> parse(CharSequence csq, ParsePosition cursor) throws ParserException {
            // Parsing reads the whole character sequence from the parse
            // position.
            int start = cursor.getIndex();
            int end = csq.length();
            if (end <= start) {
                return ONE;
            }
            String source = csq.subSequence(start, end).toString().trim();
            if (source.length() == 0) {
                return ONE;
            }
            if (!caseSensitive) {
                source = source.toUpperCase();
            }
            UCUMFormatParser parser = new UCUMFormatParser(symbolMap, new ByteArrayInputStream(source.getBytes()));
            try {
                Unit<?> result = parser.parseUnit();
                cursor.setIndex(end);
                return result;
            } catch (TokenException e) {
                if (e.currentToken != null) {
                    cursor.setErrorIndex(start + e.currentToken.endColumn);
                } else {
                    cursor.setErrorIndex(start);
                }
                throw new ParserException(e);
            } catch (TokenMgrError e) {
                cursor.setErrorIndex(start);
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        @Override
        public Unit<? extends Quantity<?>> parse(CharSequence csq) throws ParserException {
            return parse(csq, new ParsePosition(0));
        }
    }
}
