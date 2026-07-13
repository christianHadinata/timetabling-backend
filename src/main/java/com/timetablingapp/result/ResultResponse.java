package com.timetablingapp.result;

import lombok.*;

import java.time.LocalTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResultResponse {

    private Integer id;
    private Integer semesterId;
    private Integer activityId;
    private String  activityName;   // Activity.getName() convenience for the timetable view
    private Integer roomId;
    private String  roomName;
    private String  day;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean valid;

    public static ResultResponse fromEntity(Result r) {
        return ResultResponse.builder()
                .id(r.getId())
                .semesterId(r.getSemester() != null ? r.getSemester().getId() : null)
                .activityId(r.getActivity() != null ? r.getActivity().getId() : null)
                .activityName(r.getActivity() != null ? r.getActivity().getName() : null)
                .roomId(r.getRoom() != null ? r.getRoom().getId() : null)
                .roomName(r.getRoom() != null ? r.getRoom().getName() : null)
                .day(r.getDay())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .valid(r.getValid())
                .build();
    }
}
