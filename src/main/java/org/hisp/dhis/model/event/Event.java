package org.hisp.dhis.model.event;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
public class Event
{
    @JsonProperty( value = "event" )
    private String id;

    @JsonProperty
    private String program;

    @JsonProperty
    private String programStage;

    @JsonProperty
    private String enrollment;

    @JsonProperty
    private String attributeOptionCombo;

    @JsonProperty
    private String assignedUser;

    @JsonProperty
    private EventStatus status = EventStatus.ACTIVE;

    @JsonProperty
    private String orgUnit;

    @JsonProperty
    private Date createdAt;

    @JsonProperty
    private Date createdAtClient;

    @JsonProperty
    private Date updatedAt;

    @JsonProperty
    private Date updatedAtClient;

    @JsonProperty
    private Date scheduledAt;

    @JsonProperty
    private Date occurredAt;

    @JsonProperty
    private String completedBy;

    @JsonProperty
    private String storedBy;

    @JsonProperty
    private Boolean followUp;

    @JsonProperty
    private Boolean deleted;

    private List<EventDataValue> dataValues = new ArrayList<>();

    public Event( String id )
    {
        this.id = id;
    }

    public Event( String id, String program, String programStage,
        String orgUnit, Date occurredAt, List<EventDataValue> dataValues )
    {
        this( id );
        this.program = program;
        this.programStage = programStage;
        this.orgUnit = orgUnit;
        this.occurredAt = occurredAt;
        this.dataValues = dataValues;
    }
}
