package com.timetablingapp.activity.constraint;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.ActivityRepository;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.schedule.validate.ValidateLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityConstraintService {

    private final ActivityConstraintRepository repository;
    private final ActivityRepository activityRepository;
    private final ValidateLockService validateLockService;

    public List<ActivityConstraintResponse> findByActivityId(Integer activityId) {
        return repository.findByActivity_Id(activityId).stream()
                .map(ActivityConstraintResponse::fromEntity).toList();
    }

    @Transactional
    public ActivityConstraintResponse create(ActivityConstraintRequest request) {
        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Activity", "id", request.getActivityId()));
        ActivityConstraint c = new ActivityConstraint();
        c.setActivity(activity);
        c.setType(request.getType());
        c.setValue(request.getValue());
        ActivityConstraintResponse saved = ActivityConstraintResponse.fromEntity(repository.save(c));
        validateLockService.lock();
        return saved;
    }

    @Transactional
    public void delete(Integer id) {
        ActivityConstraint c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityConstraint", "id", id));
        repository.delete(c);
        validateLockService.lock();
    }
}
