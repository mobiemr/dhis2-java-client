package org.hisp.dhis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

import org.apache.commons.lang3.Validate;
import org.apache.http.Consts;
import org.apache.http.HttpHeaders;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.hisp.dhis.query.Filter;
import org.hisp.dhis.query.Operator;
import org.hisp.dhis.query.Order;
import org.hisp.dhis.query.Paging;
import org.hisp.dhis.query.Query;
import org.hisp.dhis.query.analytics.AnalyticsQuery;
import org.hisp.dhis.query.analytics.Dimension;
import org.hisp.dhis.response.Dhis2ClientException;
import org.hisp.dhis.response.HttpResponseMessage;
import org.hisp.dhis.response.ResponseMessage;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BaseDhis2
{
    protected static final String ID_FIELDS = "id,code,name,created,lastUpdated";
    protected static final String NAME_FIELDS = String.format( "%s,shortName,description", ID_FIELDS );
    protected static final String DATA_ELEMENT_FIELDS = String.format( "%1$s,aggregationType,valueType,domainType,legendSets[%1$s]", NAME_FIELDS );
    protected static final String CATEGORY_FIELDS = String.format( "%s,dataDimensionType,dataDimension", NAME_FIELDS );
    protected static final String RESOURCE_SYSTEM_INFO = "system/info";

    protected final Dhis2Config config;

    protected final ObjectMapper objectMapper;

    protected final CloseableHttpClient httpClient;

    public BaseDhis2( Dhis2Config dhis2Config )
    {
        Validate.notNull( dhis2Config, "config must be specified" );

        this.config = dhis2Config;

        this.objectMapper = new ObjectMapper();
        objectMapper.disable( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES );
        objectMapper.setSerializationInclusion( Include.NON_NULL );

        this.httpClient = HttpClientBuilder.create().build();
    }

    /**
     * Retrieves an object using HTTP GET.
     *
     * @param uriBuilder the URI builder.
     * @param query the query filters to apply.
     * @param klass the class type of the object.
     * @param <T> type.
     * @return the object.
     */
    protected <T> T getObject( URIBuilder uriBuilder, Query query, Class<T> klass )
    {
        for ( Filter filter : query.getFilters() )
        {
            String filterValue = filter.getProperty() + ":" + filter.getOperator().value() + ":" + getValue( filter );

            uriBuilder.addParameter( "filter", filterValue );
        }

        Paging paging = query.getPaging();

        if ( paging.hasPaging() )
        {
            if ( paging.hasPage() )
            {
                uriBuilder.addParameter( "page", String.valueOf( paging.getPage() ) );
            }

            if ( paging.hasPageSize() )
            {
                uriBuilder.addParameter( "pageSize", String.valueOf( paging.getPageSize() ) );
            }
        }
        else
        {
            uriBuilder.addParameter( "paging", "false" );
        }

        Order order = query.getOrder();

        if ( order.hasOrder() )
        {
            String orderValue = order.getProperty() + ":" + order.getDirection().name().toLowerCase();

            uriBuilder.addParameter( "order", orderValue );
        }

        try
        {
            return getObjectFromUrl( uriBuilder.build(), klass );
        }
        catch ( URISyntaxException ex )
        {
            throw new RuntimeException( ex );
        }
    }

    /**
     * Retrieves an analytcs object using HTTP GET.
     *
     * @param uriBuilder the URI builder.
     * @param query the query filters to apply.
     * @param klass the class type of the object.
     * @param <T> type.
     * @return the object.
     */
    protected <T> T getAnalyticsResponse( URIBuilder uriBuilder, AnalyticsQuery query, Class<T> klass )
    {
        for ( Dimension dimension : query.getDimensions() )
        {
            uriBuilder.addParameter( "dimension", dimension.getDimensionValue() );
        }

        for ( Dimension filter : query.getFilters() )
        {
            uriBuilder.addParameter( "filter", filter.getDimensionValue() );
        }

        if ( query.getAggregationType() != null )
        {
            uriBuilder.addParameter( "aggregationType", query.getAggregationType().name() );
        }

        if ( query.getStartDate() != null )
        {
            uriBuilder.addParameter( "startDate", query.getStartDate() );
        }

        if ( query.getEndDate() != null )
        {
            uriBuilder.addParameter( "endDate", query.getEndDate() );
        }

        if ( query.getSkipMeta() != null )
        {
            uriBuilder.addParameter( "skipMeta", query.getSkipMeta().toString() );
        }

        if ( query.getSkipData() != null )
        {
            uriBuilder.addParameter( "skipData", query.getSkipData().toString() );
        }

        if ( query.getSkipRounding() != null )
        {
            uriBuilder.addParameter( "skipRounding", query.getSkipRounding().toString() );
        }

        if ( query.getIgnoreLimit() != null )
        {
            uriBuilder.addParameter( "ignoreLimit", query.getIgnoreLimit().toString() );
        }

        if ( query.getOutputIdScheme() != null )
        {
            uriBuilder.addParameter( "outputIdScheme", query.getOutputIdScheme().name() );
        }

        if ( query.getInputIdScheme() != null )
        {
            uriBuilder.addParameter( "inputIdScheme", query.getInputIdScheme().name() );
        }

        try
        {
            return getObjectFromUrl( uriBuilder.build(), klass );
        }
        catch ( URISyntaxException ex )
        {
            throw new RuntimeException( ex );
        }
    }

    private Object getValue( Filter filter )
    {
        if ( Operator.IN == filter.getOperator() )
        {
            return "[" + filter.getValue() + "]";
        }
        else
        {
            return filter.getValue();
        }
    }

    /**
     * Retrieves an object.
     *
     * @param path the URL path.
     * @param id the object identifier.
     * @param klass the class type of the object.
     * @param <T> type.
     * @return the object.
     */
    protected <T> T getObject( String path, String id, Class<T> klass )
    {
        try
        {
            URI url = config.getResolvedUriBuilder()
                .pathSegment( path )
                .pathSegment( id )
                .build();

            return getObjectFromUrl( url, klass );
        }
        catch ( URISyntaxException ex )
        {
            throw new RuntimeException( ex );
        }
    }

    /**
     * Executes the given {@link HttpEntityEnclosingRequestBase}, which may be a POST or
     * PUT request.
     *
     * @param request the request.
     * @param object the object to pass as JSON in the request body.
     * @param klass the class type for the response entity.
     * @param <T> class.
     * @return a {@link ResponseMessage}.
     */
    protected <T extends HttpResponseMessage> T executeJsonPostPutRequest( HttpEntityEnclosingRequestBase request, Object object, Class<T> klass )
    {
        String jsonObject = toJsonString( object );

        withBasicAuth( request );
        request.setHeader( HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType() );
        request.setEntity( new StringEntity( jsonObject, Consts.UTF_8 ) );

        try ( CloseableHttpResponse response = httpClient.execute( request ) )
        {
            String responseBody = EntityUtils.toString( response.getEntity() );
            T responseMessage = objectMapper.readValue( responseBody, klass );

            responseMessage.setHeaders( new ArrayList<>( Arrays.asList( response.getAllHeaders() ) ) );
            responseMessage.setHttpStatusCode( response.getStatusLine().getStatusCode() );

            return responseMessage;
        }
        catch ( IOException ex )
        {
            throw newDhis2ClientException( ex );
        }
    }

    /**
     * Retrieves an object using HTTP GET.
     *
     * @param url the fully qualified URL.
     * @param klass the class type of the object.
     * @param <T> type.
     * @return the object.
     */
    protected <T> T getObjectFromUrl( URI url, Class<T> klass )
    {
        try
        {
            CloseableHttpResponse response = getJsonHttpResponse( url );
            String responseBody = EntityUtils.toString( response.getEntity() );
            return objectMapper.readValue( responseBody, klass );
        }
        catch ( IOException ex )
        {
            throw new UncheckedIOException( "Failed to fetch or parse object", ex );
        }
    }

    /**
     * Gets a {@link CloseableHttpResponse} for the given URL.
     *
     * @param url the URL.
     * @return a {@link CloseableHttpResponse}.
     */
    protected CloseableHttpResponse getJsonHttpResponse( URI url )
    {
        HttpGet request = withBasicAuth( new HttpGet( url ) );
        request.setHeader( HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType() );

        try
        {
            return httpClient.execute( request );
        }
        catch ( IOException ex )
        {
            throw new UncheckedIOException( "Request failed", ex );
        }
    }

    /**
     * Returns a basic authentication string which is generated by prepending
     * "Basic " and Base64-encoding username:password.
     *
     * @return the encoded string.
     */
    protected String getBasicAuthString()
    {
        String value = config.getUsername() + ":" + config.getPassword();

        return "Basic " + Base64.getEncoder().encodeToString( value.getBytes() );
    }

    /**
     * Adds basic authentication to the given request using the Authorization header.
     *
     * @param request the {@link HttpRequestBase}.
     * @param <T> class.
     * @return the request.
     */
    protected <T extends HttpRequestBase> T withBasicAuth( T request )
    {
        request.setHeader( HttpHeaders.AUTHORIZATION, getBasicAuthString() );
        return request;
    }

    /**
     * Serializes the given object to a JSON string.
     *
     * @param object the object to serialize.
     * @return a JSON string representation of the object.
     * @throws UncheckedIOException if the serialization failed.
     */
    protected String toJsonString( Object object )
    {
        try
        {
            return objectMapper.writeValueAsString( object );
        }
        catch ( IOException ex )
        {
            throw new UncheckedIOException( ex );
        }
    }

    /**
     * Returns a {@link Dhis2ClientException} based on the given exception.
     *
     * @param ex the exception.
     * @return a {@link Dhis2ClientException}.
     */
    protected Dhis2ClientException newDhis2ClientException( IOException ex )
    {
        int statusCode = -1;

        if ( ex instanceof HttpResponseException )
        {
            statusCode = ((HttpResponseException) ex).getStatusCode();
        }

        return new Dhis2ClientException( ex.getMessage(), ex.getCause(), statusCode );
    }

    /**
     * Converts the given array to a {@link ArrayList}.
     *
     * @param array the array.
     * @param <T> class.
     * @return a list.
     */
    protected static <T> ArrayList<T> asList( T[] array )
    {
        return new ArrayList<>( Arrays.asList( array ) );
    }
}
