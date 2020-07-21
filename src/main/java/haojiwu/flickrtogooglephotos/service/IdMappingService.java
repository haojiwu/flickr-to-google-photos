package haojiwu.flickrtogooglephotos.service;

import haojiwu.flickrtogooglephotos.model.FlickrId;
import haojiwu.flickrtogooglephotos.model.IdMapping;
import haojiwu.flickrtogooglephotos.model.IdMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class IdMappingService {

  @Autowired
  IdMappingRepository idMappingRepository;


  public void saveOrUpdate(String flickrUserId, String flickrEntityId, String googleId) {
    idMappingRepository.save(new IdMapping(new FlickrId(flickrUserId, flickrEntityId), googleId));
  }

  public void saveOrUpdateAll(List<IdMapping> idMappings) {
    idMappingRepository.saveAll(idMappings);
  }

  public Optional<IdMapping> findById(String flickrUserId, String flickrEntityId) {
    return idMappingRepository.findById(new FlickrId(flickrUserId, flickrEntityId));
  }

  public Iterable<IdMapping> findAllByIds(String flickrUserId, List<String> flickrEntityIds) {
    return idMappingRepository.findAllById(flickrEntityIds.stream()
            .map(flickrEntityId -> new FlickrId(flickrUserId, flickrEntityId))
            .collect(Collectors.toList()));
  }



}
