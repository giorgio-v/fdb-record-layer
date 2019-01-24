/*
 * Text.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2018 Apple Inc. and the FoundationDB project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apple.foundationdb.record.query.expressions;

import com.apple.foundationdb.API;
import com.apple.foundationdb.record.provider.common.text.DefaultTextTokenizer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Predicates that can be applied to a field that has been indexed with a full-text index. These
 * allow for querying on properties of the text contents, e.g., whether the text contains a
 * given token, token list, or phrase. Most of the methods here that allow for multiple
 * tokens to be supplied can either be given a single string or a list. If a single string is
 * given, then the string will be tokenized later using an appropriate tokenizer. If a list
 * is given, then the assumption is that the user has already tokenized the string and the
 * list is the result of that tokenization.
 *
 * <p>
 * This type allows the user to specify a "tokenizer name". If one is given, then it will use
 * this tokenizer to tokenize the query string (if not pre-tokenized) and will require that
 * if an index is used, it uses the tokenizer provided. If no tokenizer is specified, then
 * it will allow itself to be matched against any text index on the field and apply the
 * index's tokenizer to the query string. If no suitable index can be found and a full
 * scan with a post-filter has to be done, then a fallback tokenizer will be used both to
 * tokenize the query string as well as to tokenize the record's text. By default, this
 * is the {@link DefaultTextTokenizer} (with name "{@value DefaultTextTokenizer#NAME}"), but
 * one can specify a different one if one wishes.
 * </p>
 *
 * <p>
 * This should be created by calling the {@link Field#text() text()} method on a query
 * {@link Field} or {@link OneOfThem} instance. For example, one might call: <code>Query.field("text").text()</code>
 * to create a predicate on the <code>text</code> field's contents.
 * </p>
 *
 * @see com.apple.foundationdb.record.provider.foundationdb.indexes.TextIndexMaintainer TextIndexMaintainer
 * @see com.apple.foundationdb.record.provider.common.text.TextTokenizer TextTokenizer
 * @see DefaultTextTokenizer
 */
@API(API.Status.EXPERIMENTAL)
public abstract class Text {
    @Nullable
    private final String tokenizerName;
    @Nonnull
    private final String defaultTokenizerName;

    Text(@Nullable String tokenizerName, @Nullable String defaultTokenizerName) {
        this.tokenizerName = tokenizerName;
        this.defaultTokenizerName = defaultTokenizerName == null ? DefaultTextTokenizer.NAME : defaultTokenizerName;
    }

    /**
     * Checks if the field contains a token. This token should either
     * be generated by the tokenizer associated with this text predicate or
     * should be a plausible token that the tokenizer could have generated. This
     * token will not be further sanitized or normalized before searching for it
     * in the text.
     *
     * @param token the token to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent contains(@Nonnull String token) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_ALL, Collections.singletonList(token));
    }

    /**
     * Checks if the field contains any token matching the provided prefix.
     * This should be the beginning of a token that could be generated by the
     * tokenizer associated with this text predicate. No additional sanitization
     * or normalization of this prefix will be performed before searching
     * for it in the text.
     *
     * @param prefix the prefix to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsPrefix(@Nonnull String prefix) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_PREFIX, Collections.singletonList(prefix));
    }

    /**
     * Checks if the field contains all of the provided tokens. At query
     * evaluation time, the tokens provided here will be tokenized into
     * a list of tokens. This predicate will then return {@link Boolean#TRUE}
     * if all of the tokens (except stop words) are present in the text field,
     * {@link Boolean#FALSE} if any of them are not, and <code>null</code> if
     * either the field is <code>null</code> or if the token list contains only
     * stop words or is empty. If the same token appears multiple times in the token
     * list, then the token must only appear at <i>least</i> once in the searched
     * text to satisfy the filter (i.e., it is <i>not</i> required to appear as many
     * times in the text as in the token list).
     *
     * @param tokens the tokens to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAll(@Nonnull String tokens) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_ALL, tokens);
    }

    /**
     * Checks if the field contains all of provided tokens. This
     * behaves like {@link #containsAll(String)}, except that the token list
     * is assumed to have already been tokenized with an appropriate
     * tokenizer. No further sanitization or normalization is performed
     * on the tokens before searching for them in the text.
     *
     * @param tokens the tokens to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAll(@Nonnull List<String> tokens) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_ALL, tokens);
    }

    /**
     * Checks if the field text contains all of the provided tokens within
     * a given number of tokens. For example, in the string "a b c" (tokenized
     * by whitespace), tokens "a" and "c" are a distance of two tokens of
     * each other, so <code>containsAll("a c", 2)</code> when evaluated
     * against that string would return {@link Boolean#TRUE}, but
     * <code>containsAll("a c", 1)</code> would return {@link Boolean#FALSE}.
     * Stop words in the query string are ignored, and if there are no
     * tokens in the string (or all tokens are stop words), this will
     * evaluate to <code>null</code>. It will also evaluate to <code>null</code>
     * if the field is <code>null</code>. If the same token appears multiple times
     * in the token list, then the token must only appear at <i>least</i> once in the searched
     * text to satisfy the filter (i.e., it is <i>not</i> required to appear as many
     * times in the text as in the token list).
     *
     * @param tokens the tokens to search for
     * @param maxDistance the maximum distance (expressed in number of tokens) to allow between found
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAll(@Nonnull String tokens, int maxDistance) {
        final Comparisons.Comparison comparison = new Comparisons.TextWithMaxDistanceComparison(tokens, maxDistance, tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    /**
     * Checks if the field text contains all of the provided tokens within
     * a given number of tokens. This behaves like {@link #containsAll(String, int)}
     * except that the token list is assumed to have already been tokenized with
     * an appropriate tokenizer. No further sanitization or normalization is
     * performed on the tokens before searching for them in the text.
     *
     * @param tokens the tokens to search for
     * @param maxDistance the maximum distance (expressed in number of tokens) to allow between found
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAll(@Nonnull List<String> tokens, int maxDistance) {
        final Comparisons.Comparison comparison = new Comparisons.TextWithMaxDistanceComparison(tokens, maxDistance, tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    /**
     * Checks if the field contains tokens matching all of of the given prefixes.
     * The given {@link String} will be tokenized into multiple tokens using an
     * appropriate tokenizer. This variant of <code>containsAllPrefixes</code> is
     * <i>strict</i>, i.e., the planner will ensure that it does not return any false
     * positives when evaluated with an index scan. However, the scan can be made more
     * efficient (if false positives are acceptable) if one uses one of the other
     * variants of this function and supply <code>false</code> to the <code>strict</code>
     * parameter.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @return a new component for doing the actual evaluation
     * @see #containsAllPrefixes(String, boolean)
     */
    @Nonnull
    public QueryComponent containsAllPrefixes(@Nonnull String tokenPrefixes) {
        return containsAllPrefixes(tokenPrefixes, true);
    }

    /**
     * Checks if the field contains tokens matching all of of the given prefixes.
     * The given {@link String} will be tokenized into multiple tokens using an
     * appropriate tokenizer. The <code>strict</code> parameter determines whether this
     * comparison is <i>strictly</i> evaluated against an index. If the parameter
     * is set to <code>true</code>, then this will return no false positives, but it
     * may require that there are additional reads performed to filter out any false
     * positives that occur internally during query execution.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @param strict <code>true</code> if this should not return false positives
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAllPrefixes(@Nonnull String tokenPrefixes, boolean strict) {
        final Comparisons.Comparison comparison = new Comparisons.TextContainsAllPrefixesComparison(tokenPrefixes, strict, tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    /**
     * Checks if the field contains tokens matching all of of the given prefixes.
     * The given {@link String} will be tokenized into multiple tokens using an
     * appropriate tokenizer. The <code>strict</code> parameter behaves the same way
     * here as it does in the other overload of {@link #containsAllPrefixes(String, boolean) containsAllPrefixes()}.
     * The <code>expectedRecords</code> and <code>falsePositivePercentage</code> flags
     * can be used to tweak the behavior of underlying probabilistic data structures
     * used during query execution. See the {@link Comparisons.TextContainsAllPrefixesComparison}
     * class for more details.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @param strict <code>true</code> if this should not return any false positives
     * @param expectedRecords the expected number of records read for each prefix
     * @param falsePositivePercentage an acceptable percentage of false positives for each token prefix
     * @return a new component for doing the actual evaluation
     * @see Comparisons.TextContainsAllPrefixesComparison
     * @see #containsAllPrefixes(String, boolean)
     */
    @Nonnull
    public QueryComponent containsAllPrefixes(@Nonnull String tokenPrefixes, boolean strict, long expectedRecords, double falsePositivePercentage) {
        final Comparisons.Comparison comparison = new Comparisons.TextContainsAllPrefixesComparison(tokenPrefixes, strict, expectedRecords, falsePositivePercentage , tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    /**
     * Checks if the field contains tokens matching all of of the given prefixes.
     * This will produce a component that behaves exactly like the component returned
     * by the variant of {@link #containsAllPrefixes(String)} that takes a single
     * {@link String}, but this method assumes the token prefixes given are already
     * tokenized and normalized.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @return a new component for doing the actual evaluation
     * @see #containsAllPrefixes(String)
     */
    @Nonnull
    public QueryComponent containsAllPrefixes(@Nonnull List<String> tokenPrefixes) {
        return containsAllPrefixes(tokenPrefixes, true);
    }

    /**
     * Checks if the field contains tokens matching all of of the given prefixes.
     * This will produce a component that behaves exactly like the component returned
     * by the variant of {@link #containsAllPrefixes(String, boolean)} that takes a single
     * {@link String}, but this method assumes the token prefixes given are already
     * tokenized and normalized.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @param strict <code>true</code> if this should not return any false positives
     * @return a new component for doing the actual evaluation
     * @see #containsAllPrefixes(String, boolean)
     */
    @Nonnull
    public QueryComponent containsAllPrefixes(@Nonnull List<String> tokenPrefixes, boolean strict) {
        final Comparisons.Comparison comparison = new Comparisons.TextContainsAllPrefixesComparison(tokenPrefixes, strict, tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    /**
     * Checks if the field contains tokens matching all of of the given prefixes.
     * This will produce a component that behaves exactly like the component returned
     * by the variant of {@link #containsAllPrefixes(String, boolean, long, double)} that takes a single
     * {@link String}, but this method assumes the token prefixes given are already
     * tokenized and normalized.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @param strict <code>true</code> if this should not return any false positives
     * @param expectedRecords the expected number of records read for each prefix
     * @param falsePositivePercentage an acceptable percentage of false positives for each token prefix
     * @return a new component for doing the actual evaluation
     * @see #containsAllPrefixes(String, boolean, long, double)
     */
    @Nonnull
    public QueryComponent containsAllPrefixes(@Nonnull List<String> tokenPrefixes, boolean strict, long expectedRecords, double falsePositivePercentage) {
        final Comparisons.Comparison comparison = new Comparisons.TextContainsAllPrefixesComparison(tokenPrefixes, strict, expectedRecords, falsePositivePercentage , tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    /**
     * Checks if the field contains the given phrase. This will match
     * the given field if the given phrased (when tokenized) forms a
     * sublist of the original text. If the tokenization process removes
     * any stop words from the phrase, this will match documents that
     * contain any token in the place of the stop word. This will return
     * {@link Boolean#TRUE} if all of the tokens (except stop words)
     * can be found in the given document in the correct order,
     * {@link Boolean#FALSE} if any cannot, and <code>null</code> if the
     * phrase is empty or contains only stop words or if the field
     * itself is <code>null</code>.
     *
     * @param phrase the phrase to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsPhrase(@Nonnull String phrase) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_PHRASE, phrase);
    }

    /**
     * Checks if the field text contains the given phrase. This behaves like
     * {@link #containsPhrase(String)} except that the token list is assumed to
     * have already been tokenized with an appropriate tokenizer. No further
     * sanitization or normalization is performed on the tokens before searching
     * for them in the text. It is assumed that the order of the tokens in the
     * list is the same as the order of the tokens in the original phrase and
     * that there are no gaps (except as indicated by including the empty string to indicate
     * that there was a stop word in the original phrase).
     *
     * @param phraseTokens the tokens to search for in the order they appear in the phrase
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsPhrase(@Nonnull List<String> phraseTokens) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_PHRASE, phraseTokens);
    }

    /**
     * Checks if the field contains any of the provided tokens. At query
     * evaluation time, the tokens provided here will be tokenized into
     * a list of tokens. This predicate will then return {@link Boolean#TRUE}
     * if any of the tokens (not counting stop words) are present,
     * {@link Boolean#FALSE} if all of them are not, and <code>null</code>
     * if either the field is <code>null</code> or if the token list contains
     * only stop words or is empty.
     *
     * @param tokens the tokens to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAny(@Nonnull String tokens) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_ANY, tokens);
    }

    /**
     * Checks if the field contains all of provided tokens. This
     * behaves like {@link #containsAny(String)}, except that the token list
     * is assumed to have already been tokenized with an appropriate
     * tokenizer. No further sanitization or normalization is performed
     * on the tokens before searching for them in the text.
     *
     * @param tokens the tokens to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAny(@Nonnull List<String> tokens) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_ANY, tokens);
    }

    /**
     * Checks if the field contains a token that matches any of the given
     * prefixes. At query evaluation time, the string given is tokenized
     * using an appropriate tokenizer.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @return a new component for doing the actual evaluation
     */
    @Nonnull
    public QueryComponent containsAnyPrefix(@Nonnull String tokenPrefixes) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_ANY_PREFIX, tokenPrefixes);
    }

    /**
     * Checks if the field contains a token that matches any of the given
     * prefixes. This behaves like the variant of {@link #containsAnyPrefix(String)}
     * that takes a single {@link String} except that it assumes the token
     * prefix list has already been tokenized and normalized.
     *
     * @param tokenPrefixes the token prefixes to search for
     * @return a new component for doing the actual evaluation
     * @see #containsAnyPrefix(String)
     */
    @Nonnull
    public QueryComponent containsAnyPrefix(@Nonnull List<String> tokenPrefixes) {
        return getComponent(Comparisons.Type.TEXT_CONTAINS_ANY_PREFIX, tokenPrefixes);
    }

    @Nonnull
    private ComponentWithComparison getComponent(@Nonnull Comparisons.Type type, @Nonnull String tokens) {
        final Comparisons.Comparison comparison = new Comparisons.TextComparison(type, tokens, tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    @Nonnull
    private ComponentWithComparison getComponent(@Nonnull Comparisons.Type type, @Nonnull List<String> tokens) {
        final Comparisons.Comparison comparison = new Comparisons.TextComparison(type, tokens, tokenizerName, defaultTokenizerName);
        return getComponent(comparison);
    }

    /**
     * Create a component that uses the underlying comparison. The comparison provided
     * is guaranteed to have its type be of some text type.
     *
     * @param comparison text comparison to use when creating the component
     * @return a component that uses <code>comparison</code> as its comparison
     */
    @Nonnull
    abstract ComponentWithComparison getComponent(@Nonnull Comparisons.Comparison comparison);
}
