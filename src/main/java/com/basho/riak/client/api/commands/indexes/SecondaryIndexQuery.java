/*
 * Copyright 2014 Basho Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.basho.riak.client.api.commands.indexes;

import com.basho.riak.client.api.StreamableRiakCommand;
import com.basho.riak.client.api.commands.ChunkedResponseIterator;
import com.basho.riak.client.core.FutureOperation;
import com.basho.riak.client.core.StreamingRiakFuture;
import com.basho.riak.client.core.operations.SecondaryIndexQueryOperation;
import com.basho.riak.client.core.query.ConvertibleIterator;
import com.basho.riak.client.core.query.Location;
import com.basho.riak.client.core.query.Namespace;
import com.basho.riak.client.core.util.BinaryValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A Secondary Index Query.
 * <p>
 * Serves as a base class for all 2i queries.
 * <p>
 *
 * @param <S> the type being used for the query.
 * @author Brian Roach <roach at basho dot com>
 * @author Alex Moore <amoore at basho dot com>
 * @author Sergey Galkin <srggal at gmail dot com>
 * @since 2.0
 */
public abstract class SecondaryIndexQuery<T, S extends SecondaryIndexQuery.Response<T, ?>, U extends SecondaryIndexQuery>
        extends StreamableRiakCommand<S, U, SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Query>
{
    @FunctionalInterface
    public interface StreamableResponseCreator<T, R extends Response<T, ?>>
    {
        R createResponse(Namespace queryLocation,
                         IndexConverter<T> converter,
                         int timeout,
                         StreamingRiakFuture<SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Query> coreFuture);
    }

    @FunctionalInterface
    public interface GatherableResponseCreator<T, R extends Response<T, ?>>
    {
        R createResponse(Namespace queryLocation,
                         SecondaryIndexQueryOperation.Response coreResponse,
                         IndexConverter<T> converter);
    }

    protected final Namespace namespace;
    protected final String indexName;
    protected final BinaryValue continuation;
    protected final T match;
    protected final T start;
    protected final T end;
    protected final Integer maxResults;
    protected final boolean returnTerms;
    protected final boolean paginationSort;
    protected final String termFilter;
    protected Integer timeout;
    protected final byte[] coverageContext;
    protected final boolean returnBody;
    private final StreamableResponseCreator<T, S> streamableResponseCreator;
    private final GatherableResponseCreator<T, S> gatherableResponseCreator;

    protected SecondaryIndexQuery(Init<T, ?> builder, StreamableResponseCreator<T,S> streamableCreator,
                                  GatherableResponseCreator<T,S> gatherableResponseCreator)
    {
        this.namespace = builder.namespace;
        this.indexName = builder.indexName;
        this.continuation = builder.continuation;
        this.match = builder.match;
        this.start = builder.start;
        this.end = builder.end;
        this.maxResults = builder.maxResults;
        this.returnTerms = builder.returnTerms;
        this.paginationSort = builder.paginationSort;
        this.termFilter = builder.termFilter;
        this.timeout = builder.timeout;
        this.coverageContext = builder.coverageContext;
        this.returnBody = builder.returnBody;
        this.streamableResponseCreator = streamableCreator;
        this.gatherableResponseCreator = gatherableResponseCreator;
    }

    protected abstract IndexConverter<T> getConverter();

    /**
     * Get the location for this query.
     *
     * @return the location encompassing a bucket and bucket type.
     */
    public Namespace getNamespace()
    {
        return namespace;
    }

    /**
     * Get the full index name for this query.
     *
     * @return the index name including Riak suffix.
     */
    public String getIndexName()
    {
        return indexName;
    }

    /**
     * Get the match value supplied for this query.
     *
     * @return the single index key to match, or null if not present
     */
    public T getMatchValue()
    {
        return match;
    }

    /**
     * Get the range start value for this query.
     *
     * @return the range start, or null if not present.
     */
    public T getRangeStart()
    {
        return start;
    }

    /**
     * Get the range end value for this query.
     *
     * @return the range end value, or null if not present
     */
    public T getRangeEnd()
    {
        return end;
    }

    /**
     * Get the max number of results for this query.
     *
     * @return the max number of results, or null if not present.
     */
    public Integer getMaxResults()
    {
        return maxResults;
    }

    /**
     * Get whether this query will return both index keys and object keys.
     *
     * @return true if specified, false otherwise.
     */
    public boolean getReturnKeyAndIndex()
    {
        return returnTerms;
    }

    /**
     * Get the pagination sort setting.
     *
     * @return true if set, false otherwise.
     */
    public boolean getPaginationSort()
    {
        return paginationSort;
    }

    /**
     * Get the regex term filter for this query.
     *
     * @return the filter, or null if not set.
     */
    public String getTermFilter()
    {
        return termFilter;
    }

    /**
     * Get the continuation supplied for this query.
     *
     * @return the continuation, or null if not set.
     */
    public BinaryValue getContinuation()
    {
        return continuation;
    }

    /**
     * Get the timeout value for this query.
     *
     * @return the timeout value, or null if not set.
     */
    public Integer getTimeout()
    {
        return timeout;
    }

    @Override
    protected SecondaryIndexQueryOperation buildCoreOperation(boolean streamResults)
    {
        IndexConverter<T> converter = getConverter();

        SecondaryIndexQueryOperation.Query.Builder coreQueryBuilder =
                new SecondaryIndexQueryOperation.Query.Builder(namespace, BinaryValue.create(indexName))
                        .withContinuation(continuation)
                        .withReturnKeyAndIndex(returnTerms)
                        .withPaginationSort(paginationSort)
                        .withReturnBody(returnBody);

        if (termFilter != null)
        {
            coreQueryBuilder.withRegexTermFilter(BinaryValue.create(termFilter));
        }

        if (match != null)
        {
            coreQueryBuilder.withIndexKey(converter.convert(match));
        }
        else
        {
            coreQueryBuilder.withRangeStart(converter.convert(start))
                    .withRangeEnd(converter.convert(end));
        }

        if (maxResults != null)
        {
            coreQueryBuilder.withMaxResults(maxResults);
        }

        if (timeout != null)
        {
            coreQueryBuilder.withTimeout(timeout);
        }

        if (coverageContext != null)
        {
            coreQueryBuilder.withCoverageContext(coverageContext);
        }

        return new SecondaryIndexQueryOperation.Builder(coreQueryBuilder.build()).streamResults(streamResults).build();
    }

    @Override
    protected S convertResponse(FutureOperation<SecondaryIndexQueryOperation.Response, ?,
            SecondaryIndexQueryOperation.Query> request, SecondaryIndexQueryOperation.Response coreResponse)
    {
        return gatherableResponseCreator.createResponse(namespace, coreResponse, getConverter());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected U convertInfo(SecondaryIndexQueryOperation.Query coreInfo)
    {
        return (U)SecondaryIndexQuery.this;
    }

    @Override
    protected S createResponse(int timeout, StreamingRiakFuture<SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Query> coreFuture)
    {
        return streamableResponseCreator.createResponse(namespace, getConverter(), timeout, coreFuture);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof SecondaryIndexQuery))
        {
            return false;
        }

        SecondaryIndexQuery<?, ?, ?> that = (SecondaryIndexQuery<?, ?, ?>) o;

        if (returnTerms != that.returnTerms)
        {
            return false;
        }
        if (paginationSort != that.paginationSort)
        {
            return false;
        }
        if (namespace != null ? !namespace.equals(that.namespace) : that.namespace != null)
        {
            return false;
        }
        if (indexName != null ? !indexName.equals(that.indexName) : that.indexName != null)
        {
            return false;
        }
        if (continuation != null ? !continuation.equals(that.continuation) : that.continuation != null)
        {
            return false;
        }
        if (match != null ? !match.equals(that.match) : that.match != null)
        {
            return false;
        }
        if (start != null ? !start.equals(that.start) : that.start != null)
        {
            return false;
        }
        if (end != null ? !end.equals(that.end) : that.end != null)
        {
            return false;
        }
        if (maxResults != null ? !maxResults.equals(that.maxResults) : that.maxResults != null)
        {
            return false;
        }
        if (termFilter != null ? !termFilter.equals(that.termFilter) : that.termFilter != null)
        {
            return false;
        }
        return !(timeout != null ? !timeout.equals(that.timeout) : that.timeout != null);
    }

    @Override
    public int hashCode()
    {
        int result = namespace != null ? namespace.hashCode() : 0;
        result = 31 * result + (indexName != null ? indexName.hashCode() : 0);
        result = 31 * result + (continuation != null ? continuation.hashCode() : 0);
        result = 31 * result + (match != null ? match.hashCode() : 0);
        result = 31 * result + (start != null ? start.hashCode() : 0);
        result = 31 * result + (end != null ? end.hashCode() : 0);
        result = 31 * result + (maxResults != null ? maxResults.hashCode() : 0);
        result = 31 * result + (returnTerms ? 1 : 0);
        result = 31 * result + (paginationSort ? 1 : 0);
        result = 31 * result + (termFilter != null ? termFilter.hashCode() : 0);
        result = 31 * result + (timeout != null ? timeout.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "SecondaryIndexQuery{" +
                ", continuation: " + continuation +
                ", namespace: " + namespace +
                ", indexName: " + indexName +
                ", match: " + match +
                ", start: " + start +
                ", end: " + end +
                ", maxResults: " + maxResults +
                ", returnTerms: " + returnTerms +
                ", paginationSort: " + paginationSort +
                ", termFilter: '" + termFilter + '\'' +
                ", timeout: " + timeout +
                '}';
    }

    public enum Type
    {
        _INT("_int"),
        _BIN("_bin"),
        _BUCKET(""),
        _KEY("");

        private String suffix;

        Type(String suffix)
        {
            this.suffix = suffix;
        }

        @Override
        public String toString()
        {
            return suffix;
        }
    }

    protected interface IndexConverter<T>
    {
        T convert(BinaryValue input);

        BinaryValue convert(T input);
    }

    public static abstract class Init<S, T extends Init<S, T>>
    {
        private final Namespace namespace;
        private final String indexName;
        private volatile BinaryValue continuation;
        private volatile S match;
        private volatile S start;
        private volatile S end;
        private volatile Integer maxResults;
        private volatile boolean returnTerms;
        private volatile boolean paginationSort;
        private volatile String termFilter;
        private volatile Integer timeout;
        private volatile byte[] coverageContext;
        private volatile boolean returnBody;

        /**
         * Build a range query.
         * <p>
         * Returns all objects in Riak that have an index value
         * in the specified range.
         * </p>
         *
         * @param namespace the namespace for this query.
         * @param indexName the indexname
         * @param start     the start index value
         * @param end       the end index value
         */
        public Init(Namespace namespace, String indexName, S start, S end)
        {
            this.namespace = namespace;
            this.indexName = indexName;
            this.start = start;
            this.end = end;
        }

        /**
         * Build a match query.
         * <p>
         * Returns all objects in Riak that have an index value matching the
         * one supplied.
         * </p>
         *
         * @param namespace the namespace for this query
         * @param indexName the index name
         * @param match     the index value.
         */
        public Init(Namespace namespace, String indexName, S match)
        {
            this.namespace = namespace;
            this.indexName = indexName;
            this.match = match;
        }

        protected abstract T self();

        /**
         * Build a cover query.
         * <p>
         * Returns all objects in Riak related to the provided coverageContext.
         * </p>
         * @param namespace the namespace for this query
         * @param indexName the index name
         * @param coverageContext the cover context. An opaque binary received from coverage context entry
         *                        to be sent back to Riak for receiving appropriate data.
         */
        public Init(Namespace namespace, String indexName, byte[] coverageContext)
        {
            this.namespace = namespace;
            this.indexName = indexName;
            this.coverageContext = coverageContext;
        }

        /**
         * Set the continuation for this query.
         * <p>
         * The continuation is returned by a previous paginated query.
         * </p>
         *
         * @param continuation
         * @return a reference to this object.
         */
        public T withContinuation(BinaryValue continuation)
        {
            this.continuation = continuation;
            return self();
        }

        /**
         * Set the maximum number of results returned by the query.
         *
         * @param maxResults the number of results.
         * @return a reference to this object.
         */
        public T withMaxResults(Integer maxResults)
        {
            this.maxResults = maxResults;
            return self();
        }

        /**
         * Set whether to return the index keys with the Riak object keys.
         * Setting this to true will return both the index key and the Riak
         * object's key. The default is false (only to return the Riak object keys).
         *
         * @param returnBoth true to return both index and object keys, false to return only object keys.
         * @return a reference to this object.
         */
        public T withKeyAndIndex(boolean returnBoth)
        {
            this.returnTerms = returnBoth;
            return self();
        }

        /**
         * Set whether to sort the results of a non-paginated 2i query.
         * <p>
         * Setting this to true will sort the results in Riak before returning them.
         * </p>
         * <p>
         * Note that this is not recommended for queries that could return a large
         * result set; the overhead in Riak is substantial.
         * </p>
         *
         * @param orderByKey true to sort the results, false to return as-is.
         * @return a reference to this object.
         */
        public T withPaginationSort(boolean orderByKey)
        {
            this.paginationSort = orderByKey;
            return self();
        }

        /**
         * Set the regex to filter result terms by for this query.
         *
         * @param filter the regex to filter terms by.
         * @return a reference to this object.
         */
        public T withRegexTermFilter(String filter)
        {
            this.termFilter = filter;
            return self();
        }

        /**
         * Set the Riak-side timeout value.
         * <p>
         * By default, riak has a 60s timeout for operations. Setting
         * this value will override that default for both the
         * fetch and store operation.
         * </p>
         *
         * @param timeout the timeout in milliseconds
         * @return a reference to this object.
         */
        public T withTimeout(int timeout)
        {
            this.timeout = timeout;
            return self();
        }

        /**
         * Set the cover context for the local read
         * @param coverageContext the cover context. An opaque binary received from coverage context entry
         *                        to be sent back to Riak for receiving appropriate data.
         * @return a reference to this object.
         */
        public T withCoverageContext(byte[] coverageContext)
        {
            this.coverageContext = coverageContext;
            return self();
        }

        /**
         * Set whether to return the object values with the Riak object keys.
         *
         * It has protected access since, due to performance reasons, it might be used only for the Full Bucket Read
         * @param returnBody
         * @return a reference to this object.
         */
        protected T withReturnBody(boolean returnBody)
        {
            this.returnBody = returnBody;
            return self();
        }
    }

    /**
     * Base class for all 2i responses.
     *
     * @param <T> The type contained in the resposne.
     */
    public static class Response<T, E extends Response.Entry<T>>
            extends StreamableResponse<E, SecondaryIndexQueryOperation.Response.Entry>
    {
        final protected IndexConverter<T> converter;
        final protected SecondaryIndexQueryOperation.Response coreResponse;
        final protected Namespace queryLocation;

        protected Response(final Namespace queryLocation, IndexConverter<T> converter, final int timeout,
                 final StreamingRiakFuture<SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Query> coreFuture)
        {
            this.queryLocation = queryLocation;
            this.converter = converter;
            this.coreResponse = null;
            chunkedResponseIterator = new ChunkedResponseIterator<E, SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Response.Entry>(
                    coreFuture, timeout, null,
                    SecondaryIndexQueryOperation.Response::iterator,
                    SecondaryIndexQueryOperation.Response::getContinuation)
            {
                @SuppressWarnings("unchecked")
                @Override
                public E next()
                {
                    final SecondaryIndexQueryOperation.Response.Entry coreEntity = currentIterator.next();
                    return Response.this.createEntry(Response.this.queryLocation, coreEntity, converter);
                }
            };
        }

        protected Response(Namespace queryLocation,
                           SecondaryIndexQueryOperation.Response coreResponse,
                           IndexConverter<T> converter)
        {
            this.queryLocation = queryLocation;
            this.converter = converter;
            this.coreResponse = coreResponse;
        }

        /**
         * Get an iterator over the result data.
         *
         * If using the streaming API, this method will block
         * and wait for more data if none is immediately available.
         * It is also advisable to check {@link Thread#isInterrupted()}
         * in environments where thread interrupts must be obeyed.
         *
         * @return an iterator over the result data.
         */
        public Iterator<E> iterator()
        {
            if (isStreaming()) {
                return super.iterator();
            }

            return new ConvertibleIterator<SecondaryIndexQueryOperation.Response.Entry, E>(coreResponse.getEntryList().iterator())
            {
                @Override
                protected E convert(SecondaryIndexQueryOperation.Response.Entry e)
                {
                    return createEntry(queryLocation, e, converter);
                }
            };
        }

        /**
         * Check if this response has a continuation.
         *
         * If using the streaming API, this property's value
         * may change while data is being received, therefore
         * it is best to call it after the operation is complete.
         *
         * @return true if the response contains a continuation.
         */
        public boolean hasContinuation()
        {
            if (isStreaming())
            {
                return chunkedResponseIterator.hasContinuation();
            }

            return coreResponse.hasContinuation();
        }

        /**
         * Get the continuation from this response.
         *
         * If using the streaming API, this property's value
         * may change while data is being received, therefore
         * it is best to call it after the operation is complete.
         *
         * @return the continuation, or null if none is present.
         */
        public BinaryValue getContinuation()
        {
            if (isStreaming())
            {
                return chunkedResponseIterator.getContinuation();
            }

            return coreResponse.getContinuation();
        }

        /**
         * Check is this response contains any entries.
         *
         * If using the streaming API, this method will block
         * and wait for more data if none is immediately available.
         * It is also advisable to check {@link Thread#isInterrupted()}
         * in environments where thread interrupts must be obeyed.
         *
         * @return true if entries are present, false otherwise.
         */
        public boolean hasEntries()
        {
            if (isStreaming())
            {
                return chunkedResponseIterator.hasNext();
            }

            return !coreResponse.getEntryList().isEmpty();
        }

        /**
         * Get a list of the result entries for this response.
         *
         * @return A list of result entries.
         * @throws IllegalStateException when called while using the streaming API.
         */
        public final List<E> getEntries()
        {
            if(isStreaming())
            {
                throw new IllegalStateException("Use the iterator() while using the streaming API");
            }

            final List<SecondaryIndexQueryOperation.Response.Entry> coreEntries = coreResponse.getEntryList();
            final List<E> convertedList = new ArrayList<>(coreEntries.size());

            for (SecondaryIndexQueryOperation.Response.Entry e : coreEntries)
            {
                final E ce = createEntry(queryLocation, e, converter);
                convertedList.add(ce);
            }
            return convertedList;
        }

        @SuppressWarnings("unchecked")
        protected E createEntry(Location location, SecondaryIndexQueryOperation.Response.Entry coreEntry, IndexConverter<T> converter)
        {
            return (E)new Entry(location, coreEntry.getIndexKey(), converter);
        }

        protected final E createEntry(Namespace namespace, SecondaryIndexQueryOperation.Response.Entry coreEntry, IndexConverter<T> converter)
        {
            final Location loc = new Location(queryLocation, coreEntry.getObjectKey());
            return createEntry(loc, coreEntry, converter);
        }

        public static class Entry<T>
        {
            private final Location riakObjectLocation;
            private final BinaryValue indexKey;
            private final IndexConverter<T> converter;

            protected Entry(Location riakObjectLocation, BinaryValue indexKey, IndexConverter<T> converter)
            {
                this.riakObjectLocation = riakObjectLocation;
                this.indexKey = indexKey;
                this.converter = converter;
            }

            /**
             * Get the location for this entry.
             *
             * @return the location for this object in Riak.
             */
            public Location getRiakObjectLocation()
            {
                return riakObjectLocation;
            }

            /**
             * Get this 2i key for this entry.
             * Note this will only be present if the {@literal withKeyAndIndex(true)}
             * method was used when constructing the query.
             *
             * @return The 2i key for this entry or null if not present.
             */
            public T getIndexKey()
            {
                return converter.convert(indexKey);
            }
        }
    }
}
