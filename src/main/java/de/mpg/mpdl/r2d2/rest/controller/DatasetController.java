package de.mpg.mpdl.r2d2.rest.controller;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.mpg.mpdl.r2d2.exceptions.AuthorizationException;
import de.mpg.mpdl.r2d2.exceptions.R2d2ApplicationException;
import de.mpg.mpdl.r2d2.exceptions.R2d2TechnicalException;
import de.mpg.mpdl.r2d2.model.DatasetVersion;
import de.mpg.mpdl.r2d2.model.File;
import de.mpg.mpdl.r2d2.model.FileChunk;
import de.mpg.mpdl.r2d2.service.DatasetVersionService;
import de.mpg.mpdl.r2d2.util.Utils;

@RestController
@RequestMapping("datasets")
public class DatasetController {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatasetController.class);

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DatasetVersionService datasetVersionService;


  @PostMapping(path = "")
  public ResponseEntity<DatasetVersion> createDataset(@RequestBody DatasetVersion givenDatasetVersion, Principal prinz)
      throws R2d2TechnicalException, R2d2ApplicationException {

    DatasetVersion createdDv = datasetVersionService.create(givenDatasetVersion, Utils.toCustomPrincipal(prinz));
    return new ResponseEntity<DatasetVersion>(createdDv, HttpStatus.CREATED);
  }


  
  @PutMapping(path = "/{id}")
  public ResponseEntity<DatasetVersion> updateDataset(@PathVariable("id") String id, @RequestParam(name = "createNewVersion", defaultValue = "false") Boolean createNewVersion, @RequestBody DatasetVersion givenDatasetVersion,
      Principal prinz) throws R2d2TechnicalException, R2d2ApplicationException {

    DatasetVersion createdDv = null;
    if(createNewVersion)
    {
      createdDv = datasetVersionService.createNewVersion(UUID.fromString(id), givenDatasetVersion, Utils.toCustomPrincipal(prinz));
    }
    else
    {
      createdDv = datasetVersionService.update(UUID.fromString(id), givenDatasetVersion, Utils.toCustomPrincipal(prinz));
    }
    return new ResponseEntity<DatasetVersion>(createdDv, HttpStatus.CREATED);
  }

  @PutMapping(path = "/{id}/publish")
  public ResponseEntity<DatasetVersion> publish(@PathVariable("id") String id,
      @RequestParam(name = "lmd") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime lmd, Principal prinz)
      throws R2d2TechnicalException, R2d2ApplicationException {

    DatasetVersion publishedDv = datasetVersionService.publish(UUID.fromString(id), lmd, Utils.toCustomPrincipal(prinz));
    return new ResponseEntity<DatasetVersion>(publishedDv, HttpStatus.CREATED);
  }

  @GetMapping(path = "/{id}")
  public DatasetVersion getDataset(@PathVariable("id") String id, Principal p) throws R2d2TechnicalException, R2d2ApplicationException {

    return datasetVersionService.get(UUID.fromString(id), Utils.toCustomPrincipal(p));

  }


  @PostMapping("/{id}/files")
  public ResponseEntity<File> newFile(@PathVariable("id") String id, @RequestHeader("X-File-Name") String fileName,
      @RequestHeader(name = "X-File-Total-Chunks", required = false) Integer totalChunks,
      @RequestHeader(name = "X-File-Total-Size") Long size, HttpServletRequest req, Principal prinz)
      throws R2d2ApplicationException, AuthorizationException, R2d2TechnicalException {


    InputStream is;

    try {
      is = req.getInputStream();
    } catch (IOException e) {
      throw new R2d2TechnicalException(e);
    }


    File f = new File();
    f.setFilename(fileName);
    if (size != null) {
      f.setSize(size);
    }


    if (totalChunks != null) {
      //Init chunked upload
      try {
        if (is.read() != -1) {
          throw new R2d2ApplicationException("Body must be empty. Upload chunks after this initialization");
        }
      } catch (IOException e) {
        throw new R2d2TechnicalException(e);
      }

      f.getStateInfo().setExpectedNumberOfChunks(totalChunks);
      f = datasetVersionService.initNewFile(UUID.fromString(id), f, Utils.toCustomPrincipal(prinz));
    } else {
      //Upload single file
      f = datasetVersionService.uploadSingleFile(UUID.fromString(id), f, is, Utils.toCustomPrincipal(prinz));
    }

    BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.CREATED);

    if (f.getChecksum() != null) {
      responseBuilder.header("etag", f.getChecksum());
    }

    return responseBuilder.body(f);
  }

  @PutMapping("/{id}/files/{fileId}")
  public ResponseEntity<FileChunk> uploadFileChunk(@PathVariable("id") String id, @PathVariable("fileId") String fileId,
      @RequestHeader("X-File-Chunk-Number") int part, @RequestHeader(name = "etag", required = false) String etag,
      @RequestHeader(name = "Content-Length", required = false) Long contentLength, HttpServletRequest req, Principal prinz)
      throws R2d2ApplicationException, AuthorizationException, R2d2TechnicalException {

    InputStream is;

    try {
      is = req.getInputStream();
    } catch (IOException e) {
      throw new R2d2TechnicalException(e);
    }

    FileChunk chunk = new FileChunk();
    chunk.setClientEtag(etag);
    chunk.setNumber(part);
    if (contentLength != null) {
      chunk.setSize(contentLength);
    }
    FileChunk resultChunk =
        datasetVersionService.uploadFileChunk(UUID.fromString(id), UUID.fromString(fileId), chunk, is, Utils.toCustomPrincipal(prinz));

    ResponseEntity<FileChunk> re = ResponseEntity.status(HttpStatus.CREATED).header("etag", resultChunk.getServerEtag()).body(resultChunk);



    return re;
  }


  @GetMapping("/{id}/files/{fileId}")
  public ResponseEntity<?> download(@PathVariable("id") String datasetId, @PathVariable("fileId") String fileId, Principal prinz)
      throws R2d2ApplicationException, AuthorizationException, R2d2TechnicalException {
    InputStreamResource inputStreamResource = new InputStreamResource(
        datasetVersionService.getFileContent(UUID.fromString(datasetId), UUID.fromString(fileId), Utils.toCustomPrincipal(prinz)));
    return new ResponseEntity<InputStreamResource>(inputStreamResource, HttpStatus.OK);
  }

  @RequestMapping(value = "/search", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> searchDetailed(@RequestBody JsonNode searchSource,
      @RequestParam(name = "scroll", required = false) String scrollTimeValue, HttpServletResponse httpResponse, Principal p)
      throws AuthorizationException, R2d2TechnicalException, IOException {

    String searchSourceText = objectMapper.writeValueAsString(searchSource);
    long scrollTime = -1;

    if (scrollTimeValue != null) {

      scrollTime = TimeValue.parseTimeValue(scrollTimeValue, "test").millis();
    }

    SearchSourceBuilder ssb = Utils.parseJsonToSearchSourceBuilder(searchSourceText);

    SearchResponse resp = datasetVersionService.searchDetailed(ssb, scrollTime, Utils.toCustomPrincipal(p));


    httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    /*
    XContentBuilder builder = XContentFactory.jsonBuilder();
    resp.toXContent(builder, ToXContent.EMPTY_PARAMS);
    */


    return new ResponseEntity<String>(resp.toString(), HttpStatus.OK);
  }

}
