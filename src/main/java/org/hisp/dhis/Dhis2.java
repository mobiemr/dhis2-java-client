package org.hisp.dhis;

import static org.hisp.dhis.util.CollectionUtils.newImmutableList;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.hisp.dhis.model.Category;
import org.hisp.dhis.model.CategoryCombo;
import org.hisp.dhis.model.CategoryOption;
import org.hisp.dhis.model.CategoryOptionGroupSet;
import org.hisp.dhis.model.DataElement;
import org.hisp.dhis.model.DataElementGroup;
import org.hisp.dhis.model.DataElementGroupSet;
import org.hisp.dhis.model.Dimension;
import org.hisp.dhis.model.Objects;
import org.hisp.dhis.model.OrgUnit;
import org.hisp.dhis.model.OrgUnitGroup;
import org.hisp.dhis.model.OrgUnitGroupSet;
import org.hisp.dhis.model.OrgUnitLevel;
import org.hisp.dhis.model.PeriodType;
import org.hisp.dhis.model.Program;
import org.hisp.dhis.model.SystemInfo;
import org.hisp.dhis.model.SystemSettings;
import org.hisp.dhis.model.TableHook;
import org.hisp.dhis.model.datastore.EntryMetadata;
import org.hisp.dhis.model.datavalueset.DataValueSet;
import org.hisp.dhis.model.datavalueset.DataValueSetImportOptions;
import org.hisp.dhis.model.event.Event;
import org.hisp.dhis.model.event.Events;
import org.hisp.dhis.query.Query;
import org.hisp.dhis.query.analytics.AnalyticsQuery;
import org.hisp.dhis.request.orgunit.OrgUnitMergeRequest;
import org.hisp.dhis.request.orgunit.OrgUnitSplitRequest;
import org.hisp.dhis.response.Dhis2ClientException;
import org.hisp.dhis.response.HttpStatus;
import org.hisp.dhis.response.Response;
import org.hisp.dhis.response.datavalueset.DataValueSetResponse;
import org.hisp.dhis.response.event.EventResponse;
import org.hisp.dhis.response.job.JobCategory;
import org.hisp.dhis.response.job.JobNotification;
import org.hisp.dhis.response.object.ObjectResponse;
import org.hisp.dhis.response.objects.ObjectsResponse;
import org.hisp.dhis.util.HttpUtils;

/**
 * DHIS 2 API client for HTTP requests and responses. Request and response
 * bodies are in JSON format.
 *
 * @author Lars Helge Overland
 */
public class Dhis2
    extends BaseDhis2
{
    public Dhis2( Dhis2Config config )
    {
        super( config );
    }

    // -------------------------------------------------------------------------
    // Generic methods
    // -------------------------------------------------------------------------

    /**
     * Checks the status of the DHIS 2 instance. Returns various status codes
     * describing the status:
     *
     * <ul>
     * <li>{@link HttpStatus#OK} if instance is available and authentication is
     * successful.</li>
     * <li>{@link HttpStatus#UNAUTHORIZED} if the username and password
     * combination is not valid.</li>
     * <li>{@link HttpStatus#NOT_FOUND} if the URL is not pointing to a DHIS 2
     * instance or the DHIS 2 instance is not available.</li>
     * </ul>
     *
     * @return the HTTP status code of the response.
     */
    public HttpStatus getStatus()
    {
        URI url = HttpUtils.build( config.getResolvedUriBuilder()
            .appendPath( "system" )
            .appendPath( "info" ) );

        HttpGet request = withAuth( new HttpGet( url ) );

        try ( CloseableHttpResponse response = httpClient.execute( request ) )
        {
            int statusCode = response.getCode();
            return HttpStatus.valueOf( statusCode );
        }
        catch ( IOException ex )
        {
            // Return status code for exception of type HttpResponseException

            if ( ex instanceof HttpResponseException )
            {
                int statusCode = ((HttpResponseException) ex).getStatusCode();
                return HttpStatus.valueOf( statusCode );
            }

            throw new UncheckedIOException( ex );
        }
    }

    /**
     * Returns the URL of the DHIS 2 configuration.
     *
     * @return the URL of the DHIS 2 configuration.
     */
    public String getDhis2Url()
    {
        return config.getUrl();
    }

    /**
     * Indicates whether an object exists at the given URL path using HTTP HEAD.
     *
     * @param path the URL path relative to the API end point.
     * @return true if the object exists.
     */
    public boolean objectExists( String path )
    {
        URI url = config.getResolvedUrl( path );

        HttpHead request = withAuth( new HttpHead( url ) );

        try ( CloseableHttpResponse response = httpClient.execute( request ) )
        {
            return HttpStatus.OK.value() == response.getCode();
        }
        catch ( IOException ex )
        {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // System info
    // -------------------------------------------------------------------------

    /**
     * Retrieves the {@link SystemInfo}.
     *
     * @return the {@link SystemInfo}.
     */
    public SystemInfo getSystemInfo()
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "system" )
            .appendPath( "info" ), Query.instance(), SystemInfo.class );
    }

    // -------------------------------------------------------------------------
    // Data store
    // -------------------------------------------------------------------------

    /**
     * Saves a data store entry. The given object will be serialized to JSON
     * using <code>Jackson</code>.
     *
     * @param namespace the namespace.
     * @param key the key.
     * @param object the object.
     * @return {@link Response} holding information about the operation.
     */
    public Response saveDataStoreEntry( String namespace, String key, Object object )
    {
        return saveObject( getDataStorePath( namespace, key ), object, Response.class );
    }

    /**
     * Updates a data store entry. The given object will be serialized to JSON
     * using <code>Jackson</code>.
     *
     * @param namespace the namespace.
     * @param key the key.
     * @param object the object.
     * @return {@link Response} holding information about the operation.
     */
    public Response updateDataStoreEntry( String namespace, String key, Object object )
    {
        return updateObject( getDataStorePath( namespace, key ), object, Response.class );
    }

    /**
     * Retrieves all data store namespaces.
     *
     * @return the list of namespaces.
     */
    @SuppressWarnings( "unchecked" )
    public List<String> getDataStoreNamespaces()
    {
        return getObject( "dataStore", List.class );
    }

    /**
     * Retrieves all data store keys for the given namespace.
     *
     * @param namespace the namespace.
     * @return the list of keys.
     */
    @SuppressWarnings( "unchecked" )
    public List<String> getDataStoreKeys( String namespace )
    {
        return getObject( String.format( "dataStore/%s", namespace ), List.class );
    }

    /**
     * Retrieves a data store entry.
     *
     * @param namespace the namespace.
     * @param key the key.
     * @param type the class type of the object to retrieve.
     * @param <T> the class type.
     * @return the object associated with the given namespace and key.
     */
    public <T> T getDataStoreEntry( String namespace, String key, Class<T> type )
    {
        return getObject( getDataStorePath( namespace, key ), type );
    }

    /**
     * Retrieves metadata for a data store entry.
     *
     * @param namespace the namespace.
     * @param key the key.
     * @return the {@link EntryMetadata}.
     */
    public EntryMetadata getDataStoreEntryMetadata( String namespace, String key )
    {
        String path = String.format( "dataStore/%s/%s/metaData", namespace, key );

        return getObject( path, EntryMetadata.class );
    }

    /**
     * Removes a data store entry.
     *
     * @param namespace the namespace.
     * @param key the key.
     * @return {@link Response} holding information about the operation.
     */
    public Response removeDataStoreEntry( String namespace, String key )
    {
        return removeObject( getDataStorePath( namespace, key ), Response.class );
    }

    /**
     * Removes a namespace including all entries.
     *
     * @param namespace the namespace.
     * @return {@link Response} holding information about the operation.
     */
    public Response removeDataStoreNamespace( String namespace )
    {
        return removeObject( String.format( "dataStore/%s", namespace ), Response.class );
    }

    /**
     * Returns the path to a data store entry.
     *
     * @param namespace the namespace.
     * @param key the key.
     * @return the path to a data store entry.
     */
    private String getDataStorePath( String namespace, String key )
    {
        return String.format( "dataStore/%s/%s", namespace, key );
    }

    // -------------------------------------------------------------------------
    // Org unit
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link OrgUnit}.
     *
     * @param orgUnit the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveOrgUnit( OrgUnit orgUnit )
    {
        return saveMetadataObject( "organisationUnits", orgUnit );
    }

    /**
     * Saves or updates the list of {@link OrgUnit}.
     *
     * @param orgUnits the list of {@link OrgUnit}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveOrgUnits( List<OrgUnit> orgUnits )
    {
        return saveMetadataObjects( new Objects().setOrganisationUnits( orgUnits ) );
    }

    /**
     * Updates a {@link OrgUnit}.
     *
     * @param orgUnit the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateOrgUnit( OrgUnit orgUnit )
    {
        return updateMetadataObject( String.format( "organisationUnits/%s", orgUnit.getId() ), orgUnit );
    }

    /**
     * Removes a {@link OrgUnit}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeOrgUnit( String id )
    {
        return removeMetadataObject( String.format( "organisationUnits/%s", id ) );
    }

    /**
     * Retrieves an {@link OrgUnit}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnit}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public OrgUnit getOrgUnit( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnits" )
            .appendPath( id )
            .addParameter( "fields", ORG_UNIT_FIELDS ), Query.instance(), OrgUnit.class );
    }

    /**
     * Retrieves a list of {@link OrgUnit} which represents the sub-hierarchy of
     * the given parent org unit.
     *
     * @param id the identifier of the parent org unit.
     * @param level the org unit level, relative to the level of the parent org
     *        unit, where 1 is the level immediately below.
     * @param query the {@link Query}.
     * @return list of {@link OrgUnit}.
     */
    public List<OrgUnit> getOrgUnitSubHierarchy( String id, Integer level, Query query )
    {
        Validate.notNull( level );

        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnits" )
            .appendPath( id )
            .addParameter( "fields", ORG_UNIT_FIELDS )
            .addParameter( "level", String.valueOf( level ) ), query, Objects.class )
                .getOrganisationUnits();
    }

    /**
     * Retrieves a list of {@link OrgUnit}.
     *
     * @param query the {@link Query}.
     * @return list of {@link OrgUnit}.
     */
    public List<OrgUnit> getOrgUnits( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnits" )
            .addParameter( "fields", ORG_UNIT_FIELDS ), query, Objects.class )
                .getOrganisationUnits();
    }

    // -------------------------------------------------------------------------
    // Org unit merge and split
    // -------------------------------------------------------------------------

    /**
     * Performs an org unit split operation.
     *
     * @param request the {@link OrgUnitSplitRequest}.
     * @return the {@link Response} holding information about the operation.
     */
    public Response splitOrgUnit( OrgUnitSplitRequest request )
    {
        URI url = config.getResolvedUrl( "organisationUnits/split" );

        return executeJsonPostPutRequest( new HttpPost( url ), request, Response.class );
    }

    /**
     * Performs an org unit merge operation.
     *
     * @param request the {@link OrgUnitMergeRequest request}.
     * @return the {@link Response} holding information about the operation.
     */
    public Response mergeOrgUnits( OrgUnitMergeRequest request )
    {
        URI url = config.getResolvedUrl( "organisationUnits/merge" );

        return executeJsonPostPutRequest( new HttpPost( url ), request, Response.class );
    }

    // -------------------------------------------------------------------------
    // Org unit group
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link OrgUnitGroup}.
     *
     * @param orgUnitGroup the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveOrgUnitGroup( OrgUnitGroup orgUnitGroup )
    {
        return saveMetadataObject( "organisationUnitGroups", orgUnitGroup );
    }

    /**
     * Saves or updates the list of {@link OrgUnitGroup}.
     *
     * @param orgUnitGroups the list of {@link OrgUnitGroup}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveOrgUnitGroups( List<OrgUnitGroup> orgUnitGroups )
    {
        return saveMetadataObjects( new Objects().setOrganisationUnitGroups( orgUnitGroups ) );
    }

    /**
     * Updates a {@link OrgUnitGroup}.
     *
     * @param orgUnitGroup the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateOrgUnitGroup( OrgUnitGroup orgUnitGroup )
    {
        return updateMetadataObject( String.format( "organisationUnitGroups/%s", orgUnitGroup.getId() ), orgUnitGroup );
    }

    /**
     * Removes a {@link OrgUnitGroup}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeOrgUnitGroup( String id )
    {
        return removeMetadataObject( String.format( "organisationUnitGroups/%s", id ) );
    }

    /**
     * Retrieves an {@link OrgUnitGroup}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnitGroup}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public OrgUnitGroup getOrgUnitGroup( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroups" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), OrgUnitGroup.class );
    }

    /**
     * Retrieves a list of {@link OrgUnitGroup}.
     *
     * @param query the {@link Query}.
     * @return list of {@link OrgUnitGroup}.
     */
    public List<OrgUnitGroup> getOrgUnitGroups( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroups" )
            .addParameter( "fields", NAME_FIELDS ), query, Objects.class )
                .getOrganisationUnitGroups();
    }

    // -------------------------------------------------------------------------
    // Org unit group set
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link OrgUnitGroupSet}.
     *
     * @param orgUnitGroupSet the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveOrgUnitGroupSet( OrgUnitGroupSet orgUnitGroupSet )
    {
        return saveMetadataObject( "organisationUnitGroupSets", orgUnitGroupSet );
    }

    /**
     * Saves or updates the list of {@link OrgUnitGroupSet}.
     *
     * @param orgUnitGroupSets the list of {@link OrgUnitGroupSet}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveOrgUnitGroupSets( List<OrgUnitGroupSet> orgUnitGroupSets )
    {
        return saveMetadataObjects( new Objects().setOrganisationUnitGroupSets( orgUnitGroupSets ) );
    }

    /**
     * Updates a {@link OrgUnitGroupSet}.
     *
     * @param orgUnitGroupSet the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateOrgUnitGroupSet( OrgUnitGroupSet orgUnitGroupSet )
    {
        return updateMetadataObject( String.format( "organisationUnitGroupSets/%s", orgUnitGroupSet.getId() ),
            orgUnitGroupSet );
    }

    /**
     * Removes a {@link OrgUnitGroupSet}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeOrgUnitGroupSet( String id )
    {
        return removeMetadataObject( String.format( "organisationUnitGroupSets/%s", id ) );
    }

    /**
     * Retrieves an {@link OrgUnitGroupSet}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnitGroupSet}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public OrgUnitGroupSet getOrgUnitGroupSet( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroupSets" )
            .appendPath( id )
            .addParameter( "fields", String.format( "%s,organisationUnitGroups[%s]", NAME_FIELDS, NAME_FIELDS ) ),
            Query.instance(), OrgUnitGroupSet.class );
    }

    /**
     * Retrieves a list of {@link OrgUnitGroupSet}.
     *
     * @param query the {@link Query}.
     * @return list of {@link OrgUnitGroupSet}.
     */
    public List<OrgUnitGroupSet> getOrgUnitGroupSets( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitGroupSets" )
            .addParameter( "fields", String.format( "%s,organisationUnitGroups[%s]", NAME_FIELDS, NAME_FIELDS ) ),
            query, Objects.class )
                .getOrganisationUnitGroupSets();
    }

    // -------------------------------------------------------------------------
    // Org unit level
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link OrgUnitLevel}.
     *
     * @param id the object identifier.
     * @return the {@link OrgUnitLevel}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public OrgUnitLevel getOrgUnitLevel( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitLevels" )
            .appendPath( id )
            .addParameter( "fields", String.format( "%s,level", ID_FIELDS ) ), Query.instance(), OrgUnitLevel.class );
    }

    /**
     * Retrieves a list of {@link OrgUnitLevel}.
     *
     * @param query the {@link Query}.
     * @return list of {@link OrgUnitLevel}.
     */
    public List<OrgUnitLevel> getOrgUnitLevels( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "organisationUnitLevels" )
            .addParameter( "fields", String.format( "%s,level", ID_FIELDS ) ), query, Objects.class )
                .getOrganisationUnitLevels();
    }

    /**
     * Retrieves a list of "filled" {@link OrgUnitLevel}, meaning any gaps in
     * the persisted levels will be inserted by generated levels.
     *
     * @return list of {@link OrgUnitLevel}.
     */
    public List<OrgUnitLevel> getFilledOrgUnitLevels()
    {
        // Using array, DHIS 2 should have used a wrapper entity

        return asList( getObject( config.getResolvedUriBuilder()
            .appendPath( "filledOrganisationUnitLevels" ), Query.instance(), OrgUnitLevel[].class ) );
    }

    // -------------------------------------------------------------------------
    // Category option
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link CategoryOption}.
     *
     * @param categoryOption the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveCategoryOption( CategoryOption categoryOption )
    {
        return saveMetadataObject( "categoryOptions", categoryOption );
    }

    /**
     * Saves or updates the list of {@link CategoryOption}.
     *
     * @param categoryOptions the list of {@link CategoryOption}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveCategoryOptions( List<CategoryOption> categoryOptions )
    {
        return saveMetadataObjects( new Objects().setCategoryOptions( categoryOptions ) );
    }

    /**
     * Updates a {@link CategoryOption}.
     *
     * @param categoryOption the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateCategoryOption( CategoryOption categoryOption )
    {
        return updateMetadataObject( String.format( "categoryOptions/%s", categoryOption.getId() ), categoryOption );
    }

    /**
     * Removes a {@link CategoryOption}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeCategoryOption( String id )
    {
        return removeMetadataObject( String.format( "categoryOptions/%s", id ) );
    }

    /**
     * Retrieves an {@link CategoryOption}.
     *
     * @param id the object identifier.
     * @return the {@link CategoryOption}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public CategoryOption getCategoryOption( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptions" )
            .appendPath( id )
            .addParameter( "fields", CATEGORY_OPTION_FIELDS ), Query.instance(), CategoryOption.class );
    }

    /**
     * Retrieves a list of {@link CategoryOption}.
     *
     * @param query the {@link Query}.
     * @return list of {@link CategoryOption}.
     */
    public List<Category> getCategoryOptions( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptions" )
            .addParameter( "fields", CATEGORY_OPTION_FIELDS ), query, Objects.class )
                .getCategories();
    }

    // -------------------------------------------------------------------------
    // Category
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link Category}.
     *
     * @param category the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveCategory( Category category )
    {
        return saveMetadataObject( "categories", category );
    }

    /**
     * Saves or updates the list of {@link Category}.
     *
     * @param categories the list of {@link Category}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveCategories( List<Category> categories )
    {
        return saveMetadataObjects( new Objects().setCategories( categories ) );
    }

    /**
     * Updates a {@link Category}.
     *
     * @param categoryOption the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateCategory( CategoryOption categoryOption )
    {
        return updateMetadataObject( String.format( "categories/%s", categoryOption.getId() ), categoryOption );
    }

    /**
     * Removes a {@link Category}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeCategory( String id )
    {
        return removeMetadataObject( String.format( "categories/%s", id ) );
    }

    /**
     * Retrieves an {@link Category}.
     *
     * @param id the object identifier.
     * @return the {@link Category}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public Category getCategory( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categories" )
            .appendPath( id )
            .addParameter( "fields", CATEGORY_FIELDS ), Query.instance(), Category.class );
    }

    /**
     * Retrieves a list of {@link Category}.
     *
     * @param query the {@link Query}.
     * @return list of {@link Category}.
     */
    public List<Category> getCategories( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categories" )
            .addParameter( "fields", CATEGORY_FIELDS ), query, Objects.class )
                .getCategories();
    }

    // -------------------------------------------------------------------------
    // Category combo
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link CategoryCombo}.
     *
     * @param id the object identifier.
     * @return the {@link CategoryCombo}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public CategoryCombo getCategoryCombo( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryCombos" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), CategoryCombo.class );
    }

    /**
     * Retrieves a list of {@link CategoryCombo}.
     *
     * @param query the {@link Query}.
     * @return list of {@link CategoryCombo}.
     */
    public List<CategoryCombo> getCategoryCombos( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryCombos" )
            .addParameter( "fields", NAME_FIELDS ), query, Objects.class )
                .getCategoryCombos();
    }

    // -------------------------------------------------------------------------
    // Data element
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link DataElement}.
     *
     * @param dataElement the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveDataElement( DataElement dataElement )
    {
        return saveMetadataObject( "dataElements", dataElement );
    }

    /**
     * Saves or updates the list of {@link DataElement}.
     *
     * @param dataElements the list of {@link DataElement}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveDataElements( List<DataElement> dataElements )
    {
        return saveMetadataObjects( new Objects().setDataElements( dataElements ) );
    }

    /**
     * Updates a {@link DataElement}.
     *
     * @param dataElement the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateDataElement( DataElement dataElement )
    {
        return updateMetadataObject( String.format( "dataElements/%s", dataElement.getId() ), dataElement );
    }

    /**
     * Removes a {@link DataElement}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeDataElement( String id )
    {
        return removeMetadataObject( String.format( "dataElements/%s", id ) );
    }

    /**
     * Retrieves an {@link DataElement}.
     *
     * @param id the object identifier.
     * @return the {@link DataElement}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public DataElement getDataElement( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElements" )
            .appendPath( id )
            .addParameter( "fields", DATA_ELEMENT_FIELDS ), Query.instance(), DataElement.class );
    }

    /**
     * Retrieves a list of {@link DataElement}.
     *
     * @param query the {@link Query}.
     * @return list of {@link DataElement}.
     */
    public List<DataElement> getDataElements( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElements" )
            .addParameter( "fields", DATA_ELEMENT_FIELDS ), query, Objects.class )
                .getDataElements();
    }

    // -------------------------------------------------------------------------
    // Data element group
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link DataElement}.
     *
     * @param dataElementGroup the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveDataElementGroup( DataElementGroup dataElementGroup )
    {
        return saveMetadataObject( "dataElementGroups", dataElementGroup );
    }

    /**
     * Saves or updates the list of {@link DataElementGroup}.
     *
     * @param dataElementGroups the list of {@link DataElementGroup}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveDataElementGroups( List<DataElementGroup> dataElementGroups )
    {
        return saveMetadataObjects( new Objects().setDataElementGroups( dataElementGroups ) );
    }

    /**
     * Updates a {@link DataElementGroup}.
     *
     * @param dataElementGroup the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateDataElementGroup( DataElementGroup dataElementGroup )
    {
        return updateMetadataObject( String.format( "dataElementGroups/%s", dataElementGroup.getId() ),
            dataElementGroup );
    }

    /**
     * Removes a {@link DataElementGroup}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeDataElementGroup( String id )
    {
        return removeMetadataObject( String.format( "dataElementGroups/%s", id ) );
    }

    /**
     * Retrieves an {@link DataElementGroup}.
     *
     * @param id the object identifier.
     * @return the {@link DataElementGroup}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public DataElementGroup getDataElementGroup( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroups" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), DataElementGroup.class );
    }

    /**
     * Retrieves a list of {@link DataElementGroup}.
     *
     * @param query the {@link Query}.
     * @return list of {@link DataElementGroup}.
     */
    public List<DataElementGroup> getDataElementGroups( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroups" )
            .addParameter( "fields", NAME_FIELDS ), query, Objects.class )
                .getDataElementGroups();
    }

    // -------------------------------------------------------------------------
    // Data element group set
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link DataElementGroupSet}.
     *
     * @param id the object identifier.
     * @return the {@link DataElementGroupSet}.
     */
    public DataElementGroupSet getDataElementGroupSet( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroupSets" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), DataElementGroupSet.class );
    }

    /**
     * Retrieves a list of {@link DataElementGroupSet}.
     *
     * @param query the {@link Query}.
     * @return list of {@link DataElementGroupSet}.
     */
    public List<DataElementGroupSet> getDataElementGroupSets( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dataElementGroupSets" )
            .addParameter( "fields", NAME_FIELDS ), query, Objects.class )
                .getDataElementGroupSets();
    }

    // -------------------------------------------------------------------------
    // Program
    // -------------------------------------------------------------------------

    /**
     * Retrieves a {@link Program}.
     *
     * @param id the object identifier.
     * @return the {@link Program}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public Program getProgram( String id )
    {
        String fieldsParam = String.format(
            "%1$s,programType,categoryCombo[%1$s,categories[%2$s]]," +
                "programStages[%1$s,programStageDataElements[%1$s,dataElement[%3$s]]]," +
                "programTrackedEntityAttributes[id,code,name,trackedEntityAttribute[%4$s]]",
            NAME_FIELDS, CATEGORY_FIELDS, DATA_ELEMENT_FIELDS, TE_ATTRIBUTE_FIELDS );

        return getObject( config.getResolvedUriBuilder()
            .appendPath( "programs" )
            .appendPath( id )
            .addParameter( "fields", fieldsParam ), Query.instance(), Program.class );
    }

    /**
     * Retrieves a list of {@link Program}.
     *
     * @param query the {@link Query}.
     * @return list of {@link Program}.
     */
    public List<Program> getPrograms( Query query )
    {
        String fieldsParam = query.isExpandAssociations() ? String.format(
            "%1$s,programType,categoryCombo[%1$s,categories[%2$s]]," +
                "programStages[%1$s,programStageDataElements[%1$s,dataElement[%3$s]]]," +
                "programTrackedEntityAttributes[id,code,name,trackedEntityAttribute[%4$s]]",
            NAME_FIELDS, CATEGORY_FIELDS, DATA_ELEMENT_FIELDS, TE_ATTRIBUTE_FIELDS )
            : String.format(
                "%1$s,programType,categoryCombo[%1$s],programStages[%1$s],programTrackedEntityAttributes[%1$s]",
                NAME_FIELDS );

        return getObject( config.getResolvedUriBuilder()
            .appendPath( "programs" )
            .addParameter( "fields", fieldsParam ), query, Objects.class )
                .getPrograms();
    }

    // -------------------------------------------------------------------------
    // Category option group set
    // -------------------------------------------------------------------------

    /**
     * Retrieves an {@link CategoryOptionGroupSet}.
     *
     * @param id the object identifier.
     * @return the {@link CategoryOptionGroupSet}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public CategoryOptionGroupSet getCategoryOptionGroupSet( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptionGroupSets" )
            .appendPath( id )
            .addParameter( "fields", NAME_FIELDS ), Query.instance(), CategoryOptionGroupSet.class );
    }

    /**
     * Retrieves a list of {@link CategoryOptionGroupSet}.
     *
     * @param query the {@link Query}.
     * @return list of {@link CategoryOptionGroupSet}.
     */
    public List<CategoryOptionGroupSet> getCategoryOptionGroupSets( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "categoryOptionGroupSets" )
            .addParameter( "fields", NAME_FIELDS ), query, Objects.class )
                .getCategoryOptionGroupSets();
    }

    // -------------------------------------------------------------------------
    // Table hook
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link TableHook}.
     *
     * @param tableHook the object to save.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse saveTableHook( TableHook tableHook )
    {
        return saveMetadataObject( "analyticsTableHooks", tableHook );
    }

    /**
     * Saves or updates the list of {@link TableHook}.
     *
     * @param tableHooks the list of {@link TableHook}.
     * @return {@link ObjectsResponse} holding information about the operation.
     */
    public ObjectsResponse saveTableHooks( List<TableHook> tableHooks )
    {
        return saveMetadataObjects( new Objects().setAnalyticsTableHooks( tableHooks ) );
    }

    /**
     * Updates a {@link TableHook}.
     *
     * @param tableHook the object to update.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse updateTableHook( TableHook tableHook )
    {
        return updateMetadataObject( String.format( "analyticsTableHooks/%s", tableHook.getId() ), tableHook );
    }

    /**
     * Removes a {@link TableHook}.
     *
     * @param id the identifier of the object to remove.
     * @return {@link ObjectResponse} holding information about the operation.
     */
    public ObjectResponse removeTableHook( String id )
    {
        return removeMetadataObject( String.format( "analyticsTableHooks/%s", id ) );
    }

    /**
     * Retrieves an {@link TableHook}.
     *
     * @param id the identifier of the table hook.
     * @return the {@link TableHook}.
     * @throws Dhis2ClientException if the object does not exist.
     */
    public TableHook getTableHook( String id )
    {
        return getObject( "analyticsTableHooks", id, TableHook.class );
    }

    /**
     * Retrieves a list of {@link TableHook}.
     *
     * @param query the {@link Query}.
     * @return list of {@link TableHook}.
     */
    public List<TableHook> getTableHooks( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "analyticsTableHooks" )
            .addParameter( "fields", ID_FIELDS ), query, Objects.class )
                .getAnalyticsTableHooks();
    }

    // -------------------------------------------------------------------------
    // Dimension
    // -------------------------------------------------------------------------

    /**
     * Retrieves a {@link Dimension}.
     *
     * @param id the identifier of the dimension.
     * @return the {@link Dimension}.
     */
    public Dimension getDimension( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dimensions" )
            .appendPath( id )
            .addParameter( "fields", String.format( "%s,dimensionType", ID_FIELDS ) ), Query.instance(),
            Dimension.class );
    }

    /**
     * Retrieves a list of {@link Dimension}.
     *
     * @param query the {@link Query}.
     * @return list of {@link Dimension}.
     */
    public List<Dimension> getDimensions( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "dimensions" )
            .addParameter( "fields", String.format( "%s,dimensionType", ID_FIELDS ) ), query, Objects.class )
                .getDimensions();
    }

    // -------------------------------------------------------------------------
    // Period type
    // -------------------------------------------------------------------------

    /**
     * Retrieves a list of {@link PeriodType}.
     *
     * @param query the {@link Query}.
     * @return list of {@link PeriodType}.
     */
    public List<PeriodType> getPeriodTypes( Query query )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "periodTypes" )
            .addParameter( "fields", "frequencyOrder,name,isoDuration,isoFormat" ), query, Objects.class )
                .getPeriodTypes();
    }

    // -------------------------------------------------------------------------
    // System settings
    // -------------------------------------------------------------------------

    /**
     * Retrieves {@link SystemSettings}.
     *
     * @return system settings.
     */
    public SystemSettings getSystemSettings()
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "systemSettings" ), Query.instance(), SystemSettings.class );
    }

    // -------------------------------------------------------------------------
    // Data value set
    // -------------------------------------------------------------------------

    /**
     * Saves a {@link DataValueSet}.
     *
     * @param dataValueSet the {@link DataValueSet} to save.
     * @param options the {@link DataValueSetImportOptions}.
     * @return {@link DataValueSetResponse} holding information about the
     *         operation.
     * @throws IOException if the save process failed.
     */
    public DataValueSetResponse saveDataValueSet( DataValueSet dataValueSet, DataValueSetImportOptions options )
        throws IOException
    {
        URI url = getDataValueSetImportQuery( config.getResolvedUriBuilder()
            .appendPath( "dataValueSets" ), options );

        HttpPost request = getPostRequest( url,
            new StringEntity( toJsonString( dataValueSet ), StandardCharsets.UTF_8 ) );

        Dhis2AsyncRequest asyncRequest = new Dhis2AsyncRequest( config, httpClient, objectMapper );

        return asyncRequest.post( request, DataValueSetResponse.class );
    }

    /**
     * Saves a data value set payload in JSON format represented by the given
     * file.
     *
     * @param file the file representing the data value set JSON payload.
     * @param options the {@link DataValueSetImportOptions}.
     * @return {@link DataValueSetResponse} holding information about the
     *         operation.
     * @throws IOException if the save process failed.
     */
    public DataValueSetResponse saveDataValueSet( File file, DataValueSetImportOptions options )
        throws IOException
    {
        URI url = getDataValueSetImportQuery( config.getResolvedUriBuilder()
            .appendPath( "dataValueSets" ), options );

        HttpPost request = getPostRequest( url, new FileEntity( file, ContentType.APPLICATION_JSON ) );

        Dhis2AsyncRequest asyncRequest = new Dhis2AsyncRequest( config, httpClient, objectMapper );

        return asyncRequest.post( request, DataValueSetResponse.class );
    }

    // -------------------------------------------------------------------------
    // Analytics data value set
    // -------------------------------------------------------------------------

    /**
     * Retrieves a {@link DataValueSet}.
     *
     * @param query the {@link AnalyticsQuery}.
     * @return {@link DataValueSet}.
     */
    public DataValueSet getAnalyticsDataValueSet( AnalyticsQuery query )
    {
        return getAnalyticsResponse( config.getResolvedUriBuilder()
            .appendPath( "analytics" )
            .appendPath( "dataValueSet.json" ), query, DataValueSet.class );
    }

    /**
     * Retrieves a {@link DataValueSet} and writes it to the given file.
     *
     * @param query the {@link AnalyticsQuery}.
     * @param file the {@link File}.
     * @throws IOException if writing the response to file failed.
     */
    public void writeAnalyticsDataValueSet( AnalyticsQuery query, File file )
        throws IOException
    {
        URI url = getAnalyticsQuery( config.getResolvedUriBuilder()
            .appendPath( "analytics" )
            .appendPath( "dataValueSet.json" ), query );

        CloseableHttpResponse response = getJsonHttpResponse( url );

        writeToFile( response, file );
    }

    // -------------------------------------------------------------------------
    // Event
    // -------------------------------------------------------------------------

    /**
     * Saves an {@link Events}. The operation is synchronous.
     * <p>
     * Requires DHIS 2 version 2.35 or later.
     *
     * @param events the {@link Events}.
     * @return {@link EventResponse} holding information about the operation.
     */
    public EventResponse saveEvents( Events events )
    {
        return saveObject( config.getResolvedUriBuilder()
            .appendPath( "tracker" )
            .setParameter( "async", "false" )
            .setParameter( "importStrategy", "CREATE_AND_UPDATE" ), events, EventResponse.class );
    }

    /**
     * Retrieves an {@link Event}.
     * <p>
     * Requires DHIS 2 version 2.35 or later.
     *
     * @param id the event identifier.
     * @return the {@link Event}.
     */
    public Event getEvent( String id )
    {
        return getObject( config.getResolvedUriBuilder()
            .appendPath( "tracker" )
            .appendPath( "events" )
            .appendPath( id ), Query.instance(), Event.class );
    }

    /**
     * Removes an {@link Event}.
     * <p>
     * Requires DHIS 2 version 2.35 or later.
     *
     * @param event the {@link Event}.
     * @return {@link EventResponse} holding information about the operation.
     */
    public EventResponse removeEvent( Event event )
    {
        Validate.notNull( event.getId(), "Event identifier must be specified" );

        Events events = new Events( newImmutableList( event ) );

        return saveObject( config.getResolvedUriBuilder()
            .appendPath( "tracker" )
            .setParameter( "async", "false" )
            .setParameter( "importStrategy", "DELETE" ), events, EventResponse.class );
    }

    // -------------------------------------------------------------------------
    // Job notification
    // -------------------------------------------------------------------------

    /**
     * Retrieves a list of {@link JobNotification}.
     *
     * @param category the {@link JobCategory}.
     * @param id the job identifier.
     * @return list of {@link JobNotification}.
     */
    public List<JobNotification> getJobNotifications( JobCategory category, String id )
    {
        JobNotification[] response = getObject( config.getResolvedUriBuilder()
            .appendPath( "system" )
            .appendPath( "tasks" )
            .appendPath( category.name() )
            .appendPath( id ), Query.instance(), JobNotification[].class );

        return new ArrayList<>( Arrays.asList( response ) );
    }
}
