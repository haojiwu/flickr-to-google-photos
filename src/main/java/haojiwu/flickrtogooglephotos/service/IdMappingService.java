package haojiwu.flickrtogooglephotos.service;

import haojiwu.flickrtogooglephotos.model.IdMappingKey;
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


  public void saveOrUpdate(String userId, String sourceId, String targetId) {
    idMappingRepository.save(new IdMapping(new IdMappingKey(userId, sourceId), targetId));
  }

  public void saveOrUpdateAll(List<IdMapping> idMappings) {
    idMappingRepository.saveAll(idMappings);
  }

  public Optional<IdMapping> findById(String userId, String sourceId) {
    return idMappingRepository.findById(new IdMappingKey(userId, sourceId));
  }

  public Iterable<IdMapping> findAllByIds(String userId, List<String> sourceIds) {
    return idMappingRepository.findAllById(sourceIds.stream()
            .map(sourceId -> new IdMappingKey(userId, sourceId))
            .collect(Collectors.toList()));
  }

//  public List<String> findAllTargetByIds(String userId, List<String> sourceIds) {
//    return idMappingRepository.findAllByIdMappingKey(sourceIds.stream()
//            .map(sourceId -> new IdMappingKey(userId, sourceId))
//            .collect(Collectors.toList()));
//  }

}
