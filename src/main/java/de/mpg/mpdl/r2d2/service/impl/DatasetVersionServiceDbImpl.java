package de.mpg.mpdl.r2d2.service.impl;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.mpg.mpdl.r2d2.db.DatasetRepository;
import de.mpg.mpdl.r2d2.db.DatasetVersionRepository;
import de.mpg.mpdl.r2d2.exceptions.InvalidStateException;
import de.mpg.mpdl.r2d2.exceptions.NotFoundException;
import de.mpg.mpdl.r2d2.exceptions.OptimisticLockingException;
import de.mpg.mpdl.r2d2.exceptions.R2d2TechnicalException;
import de.mpg.mpdl.r2d2.exceptions.ValidationException;
import de.mpg.mpdl.r2d2.model.Dataset;
import de.mpg.mpdl.r2d2.model.Dataset.State;
import de.mpg.mpdl.r2d2.model.aa.User;
import de.mpg.mpdl.r2d2.model.DatasetVersion;
import de.mpg.mpdl.r2d2.search.dao.DatasetVersionDaoEs;
import de.mpg.mpdl.r2d2.service.DatasetVersionService;

@Service
public class DatasetVersionServiceDbImpl extends GenericServiceDbImpl<DatasetVersion> implements DatasetVersionService {

	private static Logger LOGGER = LoggerFactory.getLogger(DatasetVersionServiceDbImpl.class);

	@Autowired
	private DatasetVersionRepository datasetVersionRepository;

	@Autowired
	private DatasetVersionDaoEs datasetVersionIndexDao;

	@Override
	@Transactional(rollbackFor = Throwable.class)
	public DatasetVersion create(DatasetVersion datasetVersion, User user)
			throws R2d2TechnicalException, ValidationException {

		// TODO authorization?

		DatasetVersion datasetVersionToCreate = buildDatasetVersionToCreate(datasetVersion, user, 1, null);

		// TODO validation

		try {
			datasetVersionToCreate = datasetVersionRepository.save(datasetVersionToCreate);
			datasetVersionIndexDao.createImmediately(datasetVersionToCreate.getId().toString(), datasetVersionToCreate);
		} catch (Exception e) {
			throw new R2d2TechnicalException(e);
		}

		return datasetVersionToCreate;
	}

	@Override
	@Transactional(rollbackFor = Throwable.class)
	public DatasetVersion update(DatasetVersion datasetVersion, User user) throws R2d2TechnicalException,
			OptimisticLockingException, ValidationException, NotFoundException, InvalidStateException {

		return update(datasetVersion, user, false);
	}

	@Override
	@Transactional(rollbackFor = Throwable.class)
	public DatasetVersion createNewVersion(DatasetVersion datasetVersion, User user) throws R2d2TechnicalException,
			OptimisticLockingException, ValidationException, NotFoundException, InvalidStateException {
		return update(datasetVersion, user, true);
	}

	@Override
	@Transactional(rollbackFor = Throwable.class)
	public void delete(UUID id, OffsetDateTime lastModificationDate, User user)
			throws R2d2TechnicalException, OptimisticLockingException, NotFoundException, InvalidStateException {
		
		DatasetVersion datsetVersion = get(id, user);
		checkEqualModificationDate(lastModificationDate, datsetVersion.getDataset().getModificationDate());
		//TODO Complete deletion if only one version
		//TODO check state
		
		//TODO Authorization
		datasetVersionRepository.deleteById(id);
		datasetVersionIndexDao.delete(id.toString());
		// TODO Auto-generated method stub

	}

	@Override
	@Transactional(readOnly = true)
	public DatasetVersion get(UUID id, User user) throws R2d2TechnicalException, NotFoundException {


		DatasetVersion datasetVersion = datasetVersionRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Dataset version with id " + id + " not found"));
		// TODO authorization

		return datasetVersion;

	}

	@Override
	@Transactional(rollbackFor = Throwable.class)
	public void publish(UUID id, OffsetDateTime lastModificationDate, User user) throws R2d2TechnicalException,
			OptimisticLockingException, ValidationException, NotFoundException, InvalidStateException {
		// TODO Auto-generated method stub

	}

	private DatasetVersion update(DatasetVersion datasetVersion, User user, boolean createNewVersion)
			throws R2d2TechnicalException, OptimisticLockingException, ValidationException, NotFoundException,
			InvalidStateException {

		DatasetVersion datasetVersionToBeUpdated = get(datasetVersion.getId(), user);
		DatasetVersion latestVersion = datasetVersionRepository
				.getLatestVersion(datasetVersionToBeUpdated.getDataset().getId());

		if (!datasetVersionToBeUpdated.getId().equals(latestVersion.getId())) {
			throw new InvalidStateException("Only the latest dataset version can be updated. Given version: "
					+ datasetVersionToBeUpdated.getVersionNumber() + "; Latest version: "
					+ latestVersion.getVersionNumber());
		}

		// TODO authorization
		// TODO validation
		checkEqualModificationDate(datasetVersion.getDataset().getModificationDate(),
				datasetVersionToBeUpdated.getDataset().getModificationDate());

		if (createNewVersion) {
			if (!State.PUBLIC.equals(datasetVersionToBeUpdated.getState())) {
				throw new InvalidStateException(
						"A new version can only be created if the state of the latest version is public.");
			}

			datasetVersionToBeUpdated = buildDatasetVersionToCreate(datasetVersion, user,
					datasetVersionToBeUpdated.getVersionNumber() + 1, datasetVersionToBeUpdated.getDataset());
		} else {
			datasetVersionToBeUpdated = updateDatasetVersion(datasetVersion, datasetVersionToBeUpdated, user);
		}

		try {
			datasetVersionToBeUpdated = datasetVersionRepository.save(datasetVersionToBeUpdated);
			datasetVersionIndexDao.updateImmediately(datasetVersionToBeUpdated.getId().toString(),
					datasetVersionToBeUpdated);
		} catch (Exception e) {
			throw new R2d2TechnicalException(e);
		}

		return datasetVersionToBeUpdated;
	}

	private DatasetVersion updateDatasetVersion(DatasetVersion givenDatasetVersion, DatasetVersion latestVersion,
			User modifier) {

		latestVersion.setModifier(modifier);
		latestVersion.setMetadata(givenDatasetVersion.getMetadata());

		latestVersion.getDataset().setModifier(modifier);

		return latestVersion;
	}

	private DatasetVersion buildDatasetVersionToCreate(DatasetVersion givenDatasetVersion, User creator,
			int versionNumber, Dataset dataset) {

		DatasetVersion datasetVersionToCreate = new DatasetVersion();
		datasetVersionToCreate.setState(State.PRIVATE);
		datasetVersionToCreate.setMetadata(givenDatasetVersion.getMetadata());
		datasetVersionToCreate.setCreator(creator);
		datasetVersionToCreate.setModifier(creator);
		datasetVersionToCreate.setVersionNumber(versionNumber);

		Dataset datasetToCreate = dataset;
		if (datasetToCreate == null) {
			datasetToCreate = new Dataset();
			datasetToCreate.setState(State.PRIVATE);
			datasetToCreate.setCreator(creator);
		}

		datasetToCreate.setModifier(creator);
		datasetVersionToCreate.setDataset(datasetToCreate);

		return datasetVersionToCreate;

	}

}