package de.mpg.mpdl.r2d2.service.impl;

import de.mpg.mpdl.r2d2.db.DatasetRepository;
import de.mpg.mpdl.r2d2.db.DatasetVersionRepository;
import de.mpg.mpdl.r2d2.exceptions.*;
import de.mpg.mpdl.r2d2.model.*;
import de.mpg.mpdl.r2d2.model.aa.R2D2Principal;
import de.mpg.mpdl.r2d2.model.aa.UserAccount;
import de.mpg.mpdl.r2d2.search.dao.DatasetVersionDaoEs;
import de.mpg.mpdl.r2d2.search.model.DatasetVersionIto;
import de.mpg.mpdl.r2d2.util.BaseIntegrationTest;
import de.mpg.mpdl.r2d2.util.testdata.TestDataManager;
import de.mpg.mpdl.r2d2.util.testdata.TestDataFactory;
import de.mpg.mpdl.r2d2.util.testdata.builder.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for DatasetVersionServiceDbImpl.
 * 
 * @author helk
 *
 */
public class DatasetVersionServiceDbImplIT extends BaseIntegrationTest {

  @Autowired
  private DatasetVersionServiceDbImpl datasetVersionServiceDbImpl;

  @Autowired
  private DatasetVersionRepository datasetVersionRepository;

  @Autowired
  private DatasetRepository datasetRepository;

  @Autowired
  private TestDataManager testDataManager;

  //TODO: Use LatestDatasetVersionDaoImpl instead of PublicDatasetVersionDaoImpl !?
  @Autowired
  @Qualifier("PublicDatasetVersionDaoImpl")
  private DatasetVersionDaoEs datasetVersionIndexDao;

  @Test
  void testCreateDatasetVersion() throws ValidationException, AuthorizationException, R2d2TechnicalException, InvalidStateException {
    //Given
    String datasetTitle = "datasetTitle";

    DatasetVersionMetadata metadata = DatasetVersionMetadataBuilder.aDatasetVersionMetadata().title(datasetTitle).build();
    DatasetVersion datasetVersion = TestDataFactory.aDatasetVersionWithCreationAndModificationDate().metadata(metadata).build();

    UserAccount userAccount = TestDataFactory.anUser().build();
    R2D2Principal r2d2Principal =
        R2D2PrincipalBuilder.aR2D2Principal("username", "pw", new ArrayList<GrantedAuthority>()).userAccount(userAccount).build();

    this.testDataManager.persist(userAccount);

    //When
    DatasetVersion returnedDatasetVersion = this.datasetVersionServiceDbImpl.create(datasetVersion, r2d2Principal);

    //Then
    DatasetVersion datasetVersionFromDB = this.datasetVersionRepository.findLatestVersion(returnedDatasetVersion.getId());
    //QUESTION: Why getVersionID != getID for datasetVersion?
    DatasetVersionIto datasetVersionFromIndex = this.datasetVersionIndexDao.get(returnedDatasetVersion.getVersionId().toString());
    //FIXME: Maybe use the service or DB-access/SQL/Search-Index directly for verification and don't use the repositories!?

    List<DatasetVersion> createdDatasetVersions = List.of(returnedDatasetVersion, datasetVersionFromDB);

    assertThat(createdDatasetVersions).doesNotContainNull();
    assertThat(createdDatasetVersions).extracting(DatasetVersion::getMetadata).extracting(DatasetVersionMetadata::getTitle)
        .containsOnly(datasetTitle);
    assertThat(createdDatasetVersions).extracting(DatasetVersion::getMetadata).extracting(DatasetVersionMetadata::getDoi).isNotEmpty();
    assertThat(createdDatasetVersions).extracting(DatasetVersion::getVersionNumber).containsOnly(1);

    assertThat(datasetVersionFromIndex).isNotNull();
    assertThat(datasetVersionFromIndex).extracting(DatasetVersionIto::getMetadata).extracting(DatasetVersionMetadata::getTitle)
        .isEqualTo(datasetTitle);
    assertThat(datasetVersionFromIndex).extracting(DatasetVersionIto::getVersionNumber).isEqualTo(1);
  }

  @Test
  void testPublishDatasetVersion() throws InvalidStateException, R2d2TechnicalException, ValidationException, OptimisticLockingException,
      NotFoundException, AuthorizationException {
    //Given
    UserAccount userAccount = TestDataFactory.anUser().build();

    String datasetTitle = "datasetTitle";

    DatasetVersionMetadata metadata = DatasetVersionMetadataBuilder.aDatasetVersionMetadata().title(datasetTitle)
        .authors(Arrays.asList(PersonBuilder.aPerson().familyName("AuthorFamilyName").givenName("AuthorGivenName")
            .affiliations(Arrays.asList(AffiliationBuilder.anAffiliation().organization("Organization").build())).build()))
        .build();
    Dataset dataset = TestDataFactory.aDatasetWithCreationAndModificationDate().creator(userAccount).build();
    DatasetVersion datasetVersion = TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).metadata(metadata)
        .state(Dataset.State.PRIVATE).build();

    this.testDataManager.persist(userAccount, datasetVersion);

    //FIXME: Simplify Grants/Authorization management in tests (and in prod code?)
    R2D2Principal r2d2Principal =
        R2D2PrincipalBuilder.aR2D2Principal("username", "pw", new ArrayList<GrantedAuthority>()).userAccount(userAccount).build();

    //When
    DatasetVersion returnedDatasetVerion =
        this.datasetVersionServiceDbImpl.publish(datasetVersion.getId(), datasetVersion.getModificationDate(), r2d2Principal);

    //Then
    assertThat(returnedDatasetVerion).isNotNull();
    assertThat(returnedDatasetVerion).extracting(DatasetVersion::getState).isEqualTo(Dataset.State.PUBLIC);
    //TODO: Add further assertions
  }

  @Test
  void testUpdatePublicDatasetVersion() throws InvalidStateException, R2d2TechnicalException, ValidationException,
      OptimisticLockingException, NotFoundException, AuthorizationException {
    //Given
    UserAccount userAccount = TestDataFactory.anUser().build();

    String datasetTitle = "datasetTitle";

    DatasetVersionMetadata metadata = DatasetVersionMetadataBuilder.aDatasetVersionMetadata().title(datasetTitle).build();
    Dataset dataset = TestDataFactory.aDatasetWithCreationAndModificationDate().creator(userAccount).build();
    DatasetVersion datasetVersion = TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).metadata(metadata)
        .state(Dataset.State.PUBLIC).build();

    this.testDataManager.persist(userAccount, datasetVersion);

    String newDescription = "New Description";
    datasetVersion.getMetadata().setDescription(newDescription);

    R2D2Principal r2d2Principal =
        R2D2PrincipalBuilder.aR2D2Principal("username", "pw", new ArrayList<GrantedAuthority>()).userAccount(userAccount).build();

    //When
    DatasetVersion returnedDatasetVersion = this.datasetVersionServiceDbImpl.update(datasetVersion.getId(), datasetVersion, r2d2Principal);

    //Then
    assertThat(returnedDatasetVersion).isNotNull();
    assertThat(returnedDatasetVersion).extracting(DatasetVersion::getMetadata).extracting(DatasetVersionMetadata::getDescription)
        .isEqualTo(newDescription);
    //TODO: Add further assertions
  }

  @Test
  void testWithdrawDatasetVersion() throws ValidationException, R2d2TechnicalException, AuthorizationException, OptimisticLockingException,
      NotFoundException, InvalidStateException {
    //Given
    UserAccount userAccount = TestDataFactory.anUser().build();
    R2D2Principal r2d2Principal =
        R2D2PrincipalBuilder.aR2D2Principal("username", "pw", new ArrayList<GrantedAuthority>()).userAccount(userAccount).build();

    Dataset dataset = TestDataFactory.aDatasetWithCreationAndModificationDate().creator(userAccount).state(Dataset.State.PUBLIC).build();
    DatasetVersion datasetVersion =
        TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).state(Dataset.State.PUBLIC).build();

    this.testDataManager.persist(userAccount, datasetVersion);

    String withdrawComment = "Withdraw comment";

    //When
    this.datasetVersionServiceDbImpl.withdraw(datasetVersion.getId(), datasetVersion.getModificationDate(), withdrawComment, r2d2Principal);

    //Then
    List<DatasetVersion> datasetVersionsFromDB = this.datasetVersionRepository.findAllByDatasetId(datasetVersion.getId());
    Optional<Dataset> datasetOptional = this.datasetRepository.findById(datasetVersion.getId());
    DatasetVersionIto datasetVersionFromIndex = this.datasetVersionIndexDao.get(datasetVersion.getVersionId().toString());

    assertThat(datasetVersionsFromDB).isNotEmpty();
    assertThat(datasetVersionsFromDB).extracting(DatasetVersion::getState).containsOnly(Dataset.State.WITHDRAWN);
    assertThat(Optional.of(datasetOptional)).isPresent();
    assertThat(datasetOptional).map(Dataset::getState).containsSame(Dataset.State.WITHDRAWN);
    assertThat(datasetOptional).map(Dataset::getWithdrawComment).contains(withdrawComment);
    assertThat(datasetVersionFromIndex).isNotNull();
    assertThat(datasetVersionFromIndex).extracting(DatasetVersionIto::getState).isEqualTo(Dataset.State.WITHDRAWN);
    //TODO: Add further assertions
  }

  @Test
  void testGetLatestDatasetVersionPublic() throws AuthorizationException, NotFoundException, R2d2TechnicalException {
    //Given
    Dataset dataset = TestDataFactory.aDatasetWithCreationAndModificationDate().build();

    DatasetVersion datasetVersion1 =
        TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).state(Dataset.State.PUBLIC).build();
    DatasetVersion datasetVersion2 = TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset)
        .state(Dataset.State.PUBLIC).versionNumber(2).publicationComment("publication Comment").build();
    DatasetVersion datasetVersion3 = TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset)
        .state(Dataset.State.PRIVATE).versionNumber(3).build();

    R2D2Principal r2d2Principal = R2D2PrincipalBuilder.aR2D2Principal("username", "pw", new ArrayList<>())
        .userAccount(UserAccountBuilder.anUserAccount().build()).build();

    this.testDataManager.persist(datasetVersion1, datasetVersion2, datasetVersion3);

    //When
    DatasetVersion returnedDatasetVersion = this.datasetVersionServiceDbImpl.getLatest(dataset.getId(), r2d2Principal);

    //Then
    assertThat(returnedDatasetVersion).isNotNull();
    assertThat(returnedDatasetVersion.getVersionNumber()).isEqualTo(2);
  }

  @Test
  void testGetDatasetVersion() throws AuthorizationException, NotFoundException, R2d2TechnicalException {
    //Given
    UserAccount userAccount = TestDataFactory.anUser().build();
    R2D2Principal r2d2Principal =
        R2D2PrincipalBuilder.aR2D2Principal("username", "pw", new ArrayList<GrantedAuthority>()).userAccount(userAccount).build();

    Dataset dataset = TestDataFactory.aDatasetWithCreationAndModificationDate().creator(userAccount).build();

    DatasetVersion datasetVersion1 = TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).build();
    DatasetVersion datasetVersion2 =
        TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).versionNumber(2).build();
    DatasetVersion datasetVersion3 =
        TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).versionNumber(3).build();

    this.testDataManager.persist(userAccount, datasetVersion1, datasetVersion2, datasetVersion3);
    VersionId versionId2 = datasetVersion2.getVersionId();

    //When
    DatasetVersion returnedDatasetVersion = this.datasetVersionServiceDbImpl.get(versionId2, r2d2Principal);

    //Then
    assertThat(returnedDatasetVersion).isNotNull();
    assertThat(returnedDatasetVersion.getVersionNumber()).isEqualTo(2);
    assertThat(returnedDatasetVersion.getVersionId()).isEqualTo(versionId2);
  }

  @Test
  void testGetFileForDataset() throws AuthorizationException, R2d2TechnicalException, NotFoundException {
    //Given
    UserAccount userAccount = TestDataFactory.anUser().build();
    R2D2Principal r2d2Principal =
        R2D2PrincipalBuilder.aR2D2Principal("username", "pw", new ArrayList<GrantedAuthority>()).userAccount(userAccount).build();

    Dataset dataset = TestDataFactory.aDatasetWithCreationAndModificationDate().creator(userAccount).build();
    DatasetVersion datasetVersion = TestDataFactory.aDatasetVersionWithCreationAndModificationDate().dataset(dataset).build();

    File file = TestDataFactory.aFileWithDates().datasets(Collections.singleton(datasetVersion)).build();

    this.testDataManager.persist(userAccount, datasetVersion, file);

    //When
    File returnedFile = this.datasetVersionServiceDbImpl.getFileForDataset(datasetVersion.getVersionId(), file.getId(), r2d2Principal);

    //Then
    assertThat(returnedFile).isNotNull();
    assertThat(returnedFile.getId()).isEqualTo(file.getId());
  }

}
