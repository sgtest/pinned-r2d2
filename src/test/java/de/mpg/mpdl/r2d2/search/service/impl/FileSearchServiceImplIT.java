package de.mpg.mpdl.r2d2.search.service.impl;

import de.mpg.mpdl.r2d2.exceptions.AuthorizationException;
import de.mpg.mpdl.r2d2.exceptions.R2d2TechnicalException;
import de.mpg.mpdl.r2d2.model.Dataset;
import de.mpg.mpdl.r2d2.model.DatasetVersion;
import de.mpg.mpdl.r2d2.model.File;
import de.mpg.mpdl.r2d2.model.aa.R2D2Principal;
import de.mpg.mpdl.r2d2.model.aa.UserAccount;
import de.mpg.mpdl.r2d2.search.model.*;
import de.mpg.mpdl.r2d2.util.BaseIntegrationTest;
import de.mpg.mpdl.r2d2.util.testdata.TestDataFactory;
import de.mpg.mpdl.r2d2.util.testdata.TestDataIndexer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class FileSearchServiceImplIT extends BaseIntegrationTest {

  @Autowired
  private FileSearchServiceImpl fileSearchService;

  @Autowired
  private TestDataIndexer testDataIndexer;

  @Test
  void testSearchFilesForDatasetEmptyQuery() throws R2d2TechnicalException, AuthorizationException {
    //Given
    UserAccount userAccount = TestDataFactory.anUser().id(UUID.randomUUID()).build();
    R2D2Principal r2d2Principal = TestDataFactory.aR2D2Principal().userAccount(userAccount).build();

    Dataset dataset = TestDataFactory.aDataset().id(UUID.randomUUID()).creator(userAccount).build();
    DatasetVersion datasetVersion = TestDataFactory.aDatasetVersion().dataset(dataset).build();

    File attachedFile = TestDataFactory.aFile().id(UUID.randomUUID()).creator(userAccount).state(File.UploadState.ATTACHED)
        .datasets(Collections.singleton(datasetVersion)).build();
    File unattachedFile = TestDataFactory.aFile().id(UUID.randomUUID()).creator(userAccount).state(File.UploadState.COMPLETE).build();

    this.testDataIndexer.index(datasetVersion, attachedFile, unattachedFile);

    SearchQuery searchQuery = new SearchQuery();

    //When
    SearchResult<FileIto> searchResult = fileSearchService.searchFilesForDataset(searchQuery, datasetVersion.getVersionId(), r2d2Principal);

    //Then
    assertThat(searchResult).isNotNull().extracting(SearchResult::getTotal).isEqualTo(1);
    assertThat(searchResult.getHits())
        .flatExtracting((Function<? super SearchRecord<FileIto>, ?>) searRecord -> searRecord.getSource().getId())
        .containsExactly(attachedFile.getId());
  }

}
