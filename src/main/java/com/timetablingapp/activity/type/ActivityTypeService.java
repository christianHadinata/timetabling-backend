package com.timetablingapp.activity.type;

import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityTypeService
        implements BaseCrudService<ActivityTypeResponse, ActivityTypeRequest, Integer> {

    private final ActivityTypeRepository repository;
    private final ActivityRepository activityRepository;

    @Override
    public List<ActivityTypeResponse> findAll() {
        return repository.findAll().stream().map(ActivityTypeResponse::fromEntity).toList();
    }

    @Override
    public ActivityTypeResponse findById(Integer id) {
        return ActivityTypeResponse.fromEntity(getOrThrow(id));
    }

    @Override
    @Transactional
    public ActivityTypeResponse create(ActivityTypeRequest request) {
        ActivityType t = new ActivityType();
        t.setName(request.getName());
        return ActivityTypeResponse.fromEntity(repository.save(t));
    }

    @Override
    @Transactional
    public ActivityTypeResponse update(Integer id, ActivityTypeRequest request) {
        ActivityType t = getOrThrow(id);
        t.setName(request.getName());
        return ActivityTypeResponse.fromEntity(repository.save(t));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        ActivityType t = getOrThrow(id);
        // Legacy ActivityTypeController@destroy guard.
        if (activityRepository.existsByActivityType_Id(id)) {
            throw new BadRequestException(
                "Cannot delete activity type: it is still used by one or more activities.");
        }
        repository.delete(t);
    }

    private ActivityType getOrThrow(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityType", "id", id));
    }
}
