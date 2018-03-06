/*
 * Demoiselle Framework
 *
 * License: GNU Lesser General Public License (LGPL), version 3 or later.
 * See the lgpl.txt file in the root directory or <https://www.gnu.org/licenses/lgpl.html>.
 */
package org.demoiselle.jee.crud.pagination;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.demoiselle.jee.core.api.crud.Result;
import org.demoiselle.jee.crud.AbstractDAO;
import org.demoiselle.jee.crud.CrudUtilHelper;
import org.demoiselle.jee.crud.DemoiselleCrud;
import org.demoiselle.jee.crud.DemoiselleRequestContext;
import org.demoiselle.jee.crud.ReservedHTTPHeaders;
import org.demoiselle.jee.crud.ReservedKeyWords;
import org.demoiselle.jee.crud.configuration.DemoiselleCrudConfig;

/**
 * Class responsible for managing the 'range' parameter comes from Url Query
 * String.
 *
 * Ex:
 *
 * Given a request
 * <pre>
 * GET @{literal http://localhost:8080/api/users?range=0-10}
 * </pre>
 *
 * This class will processing the request above and parse the range parameters
 * to {@link DemoiselleRequestContext} object.
 *
 * This object will be use on {@link AbstractDAO} class to execute the
 * pagination on database.
 *
 */
@RequestScoped
public class PaginationHelper {

    private UriInfo uriInfo;

    private ResourceInfo resourceInfo;

    @Inject
    private DemoiselleRequestContext drc;

    @Inject
    private PaginationHelperMessage message;

    @Inject
    DemoiselleCrudConfig crudConfig;

    private static final Logger logger = Logger.getLogger(PaginationHelper.class.getName());

    public PaginationHelper() {
    }

    public PaginationHelper(ResourceInfo resourceInfo, UriInfo uriInfo, DemoiselleCrudConfig crudConfig, DemoiselleRequestContext drc, PaginationHelperMessage message) {
        this.resourceInfo = resourceInfo;
        this.uriInfo = uriInfo;
        this.drc = drc;
        this.crudConfig = crudConfig;
        this.message = message;
    }

    /**
     * Open the request query string to extract values from 'range' parameter
     * and fill the limit and offset parameters from {@link DemoiselleRequestContext#getPaginationContext()}.
     *
     * @param resourceInfo ResourceInfo
     * @param uriInfo UriInfo
     */
    public void execute(ResourceInfo resourceInfo, UriInfo uriInfo) {
        fillObjects(resourceInfo, uriInfo);

        drc.getPaginationContext().setPaginationEnabled(isPaginationEnabled());

        if (drc.getPaginationContext().isPaginationEnabled()) {

            if (isRequestPagination()) {
                checkAndFillRangeValues();
            } else {
                drc.getPaginationContext().setLimit(getQuantityPerPage() - 1);
                drc.getPaginationContext().setOffset(new Integer(0));
            }
        }

        if (hasDemoiselleCrudAnnotation() && isRequestPagination() && !drc.getDemoiselleCrudAnnotation().enablePagination()) {
            throw new IllegalArgumentException(message.paginationIsNotEnabled());
        }
    }

    private void fillObjects(ResourceInfo resourceInfo, UriInfo uriInfo) {
        this.resourceInfo = resourceInfo == null ? this.resourceInfo : resourceInfo;
        this.uriInfo = uriInfo == null ? this.uriInfo : uriInfo;
    }

    /**
     * Check the pagination is enabled
     *
     * @return pagination enabled/disabled
     */
    private Boolean isPaginationEnabled() {
        if (!crudConfig.isPaginationEnabled()) {
            return false;
        }

        if (hasDemoiselleCrudAnnotation()) {
            DemoiselleCrud demoiselleCrudAnnotation = drc.getDemoiselleCrudAnnotation();
            return demoiselleCrudAnnotation.enablePagination();
        }

        return true;
    }

    /**
     * Check if the actual request has the 'range' parameter on query string
     *
     * @return is request pagination or not
     */
    private Boolean isRequestPagination() {
        // Verify if contains 'range' in url
        if (uriInfo.getQueryParameters().containsKey(ReservedKeyWords.DEFAULT_RANGE_KEY.getKey())) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * Check if the value of 'range' parameter is valid using the rules:
     *
     * - Value formatted like offset-limit (range=0-10); - The 'offset' and
     * 'limit' should be a integer; - The 'offset' should be less than or equals
     * 'limit';
     *
     *
     * @throws IllegalArgumentException The format is invalid
     */
    private void checkAndFillRangeValues() throws IllegalArgumentException {
        List<String> rangeList = uriInfo.getQueryParameters().get(ReservedKeyWords.DEFAULT_RANGE_KEY.getKey());
        fillLimitAndOffsetFromRange(drc, getQuantityPerPage(), rangeList);
    }

    private void fillLimitAndOffsetFromRange(DemoiselleRequestContext drc, Integer quantityPerPage, List<String> rangeList) {
        PaginationContext paginationContext = createContextFromRange(message, quantityPerPage, rangeList);
        if (paginationContext != null) {
            drc.setPaginationContext(paginationContext);
        } else {
            drc.setPaginationContext(PaginationContext.disabledPagination());
        }
    }

    public static PaginationContext createContextFromRange(PaginationHelperMessage message, Integer quantityPerPage, List<String> rangeList) {
        Integer limit, offset;
        if (!rangeList.isEmpty()) {
            String range[] = rangeList.get(0).split("-");
            if (range.length == 2) {
                String strOffset = range[0];
                String setLimit = range[1];

                try {
                    offset = new Integer(strOffset);
                    limit = new Integer(setLimit);

                    if (offset > limit) {
                        logInvalidRangeParameters(rangeList.get(0));
                        throw new IllegalArgumentException(message.invalidRangeParameters());
                    }

                    if (((limit - offset) + 1) > quantityPerPage) {
                        logger.warning(message.defaultPaginationNumberExceed(quantityPerPage) + ", [limit = " + limit + ", offset = "+offset+"]");
                        throw new IllegalArgumentException(message.defaultPaginationNumberExceed(quantityPerPage));
                    }

                } catch (NumberFormatException nfe) {
                    logInvalidRangeParameters(rangeList.get(0));
                    throw new IllegalArgumentException(message.invalidRangeParameters());
                }
            } else {
                logInvalidRangeParameters(rangeList.get(0));
                throw new IllegalArgumentException(message.invalidRangeParameters());
            }
            return new PaginationContext(limit, offset, true);
        }
        return null;
    }


    /**
     * Get default pagination number, if the target method is annotated with
     * Search annotation the default annotation will be
     * {@link DemoiselleCrud#pageSize()} otherwise the default pagination will be
     * {@link DemoiselleCrudConfig#getDefaultPagination()} value;
     *
     * @return Number per page
     */
    private Integer getQuantityPerPage() {
        Integer quantityPerPage = crudConfig.getDefaultPagination();

        if (hasDemoiselleCrudAnnotation()) {
            DemoiselleCrud paginateResult = drc.getDemoiselleCrudAnnotation();
            if (paginateResult.pageSize() >= 0) {
                quantityPerPage = paginateResult.pageSize();
            }
        }
        return quantityPerPage;
    }

    private Boolean hasDemoiselleCrudAnnotation() {
        return drc.getDemoiselleCrudAnnotation() != null;
    }

    /**
     * Check if the actual response is a Partial Content (HTTP 206 code)
     *
     * @return is partial content or not
     * @param result The result object from the current response
     */
    public Boolean isPartialContentResponse(Result result) {
        Integer limit = result.getPaginationContext().getLimit() == null ? 0 : result.getPaginationContext().getLimit();
        Long count = result.getCount() == null ? 0 : result.getCount();
        return !((limit + 1) >= count);
    }

    private static void logInvalidRangeParameters(String range) {
        logger.warning(CDI.current().select(PaginationHelperMessage.class).get().invalidRangeParameters() + ", [params: " + range + "]");
    }

    /**
     * Build the 'Content-Range' HTTP Header value.
     *
     * @return 'Content-Range' value
     */
    private String buildContentRange(Result result) {
        Integer limit = result.getPaginationContext().getLimit() == null ? getQuantityPerPage() - 1 : result.getPaginationContext().getLimit();
        Integer offset = result.getPaginationContext().getOffset() == null ? 0 : result.getPaginationContext().getOffset();
        Long count = result.getCount() == null ? 0 : result.getCount();
        return offset + "-" + (limit.equals(0) ? count - 1 : limit) + "/" + count;
    }

    /**
     * Build the 'Accept-Range' HTTP Header value.
     *
     * @param entityClass The entity class of the current request, if any
     * @return 'Accept-Range' value
     */
    public String buildAcceptRange(Class<?> entityClass) {
        String resource = "";
        String resourceClass = "";
        if (resourceInfo != null) {
            Class<?> targetClass = CrudUtilHelper.getTargetClass(resourceInfo);
            if (targetClass != null) {
                resourceClass = targetClass.getSimpleName().toLowerCase();
            } else {
                resourceClass = "unknown";
            }

            resource = resourceClass + " " + getQuantityPerPage();
        }

        return resource;
    }

    /**
     * Set the 'Content-Range', 'Accept-Range', 'Link' and
     * 'Access-Control-Expose-Headers' HTTP headers;
     *
     * @param resourceInfo ResourceInfo
     * @param uriInfo UriInfo
     *
     * @param result The result object generated for the current request
     * @return A map with HTTP headers
     */
    public Map<String, String> buildHeaders(ResourceInfo resourceInfo, UriInfo uriInfo, Result result) {
        fillObjects(resourceInfo, uriInfo);
        Map<String, String> headers = new ConcurrentHashMap<>();

        Class<?> entityClass;
        if (result != null) {
            entityClass = result.getEntityClass();
        } else {
            entityClass = null;
        }
        if (result != null && result.getPaginationContext() != null && result.getPaginationContext().isPaginationEnabled()) {
            headers.putIfAbsent(ReservedHTTPHeaders.HTTP_HEADER_CONTENT_RANGE.getKey(), buildContentRange(result));
            String acceptRange = buildAcceptRange(entityClass);
            headers.putIfAbsent(ReservedHTTPHeaders.HTTP_HEADER_ACCEPT_RANGE.getKey(), acceptRange);
            String linkHeader = buildLinkHeader(result);

            if (!linkHeader.isEmpty()) {
                headers.putIfAbsent(HttpHeaders.LINK, linkHeader);
            }
        }

        return headers;
    }

    /**
     * Build the 'Link' HTTP header value
     *
     * @return 'Link' value
     */
    private String buildLinkHeader(Result result) {
        StringBuffer sb = new StringBuffer();
        String url = uriInfo.getRequestUri().toString();
        url = url.replaceFirst(".range=([^&]*)", "");

        if (result.getPaginationContext().getOffset() == null) {
            result.getPaginationContext().setOffset(new Integer(0));
        }

        if (result.getPaginationContext().getLimit() == null) {
            result.getPaginationContext().setLimit(getQuantityPerPage() - 1);
        }

        Integer offset = result.getPaginationContext().getOffset() + 1;
        Integer limit = result.getPaginationContext().getLimit() + 1;
        Integer quantityPerPage = (limit - offset) + 1;

        if (uriInfo.getQueryParameters().isEmpty()
                || (uriInfo.getQueryParameters().size() == 1 && uriInfo.getQueryParameters().containsKey(ReservedKeyWords.DEFAULT_RANGE_KEY.getKey()))) {
            url += "?" + ReservedKeyWords.DEFAULT_RANGE_KEY.getKey() + "=";
        } else {
            url += "&" + ReservedKeyWords.DEFAULT_RANGE_KEY.getKey() + "=";
        }

        if (!isFirstPage(result)) {
            Integer prevPageRangeInit = (result.getPaginationContext().getOffset() - quantityPerPage) < 0 ? 0 : (result.getPaginationContext().getOffset() - quantityPerPage);
            Integer firstRange2 = quantityPerPage - 1 < result.getPaginationContext().getOffset() - 1 ? quantityPerPage - 1 : result.getPaginationContext().getOffset() - 1;

            String firstPage = url + 0 + "-" + firstRange2;
            String prevPage = url + prevPageRangeInit + "-" + (result.getPaginationContext().getOffset() - 1);

            sb.append("<").append(firstPage).append(">; rel=\"first\",");
            sb.append("<").append(prevPage).append(">; rel=\"prev\",");
        }

        if (isPartialContentResponse(result)) {
            String nextPage = url + (result.getPaginationContext().getOffset() + quantityPerPage) + "-" + (2 * quantityPerPage + result.getPaginationContext().getOffset() - 1);
            String lastPage = url + (result.getCount() - quantityPerPage) + "-" + (result.getCount() - 1);

            if (offset + quantityPerPage >= result.getCount() - 1) {
                nextPage = lastPage;
            }

            sb.append("<").append(nextPage).append(">; rel=\"next\",");
            sb.append("<").append(lastPage).append(">; rel=\"last\"");
        }

        return sb.toString();
    }

    private Boolean isFirstPage(Result result) {
        return result.getPaginationContext().getOffset().equals(0);
    }

    public void buildAcceptRangeWithResponse(ContainerResponseContext response) {
        if (response != null) {
            Class<?> entityClass = getEntityClassForResponse(response);
            String acceptRangeHeader = buildAcceptRange(entityClass);
            if (acceptRangeHeader != null) {
                response.getHeaders().putSingle(ReservedHTTPHeaders.HTTP_HEADER_ACCEPT_RANGE.getKey(), acceptRangeHeader);
            }
        }

    }

    private Class<?> getEntityClassForResponse(ContainerResponseContext response) {
        if (response.getEntity() instanceof Result) {
            return ((Result)response.getEntity()).getEntityClass();
        }
        return null;
    }

}
