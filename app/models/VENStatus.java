package models;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity(name = "StatusObject")
@Table(name = "STATUSOBJECT")
public class VENStatus {
	
	@Id 
	@Column(name = "ID")
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;
	
	@Column(name = "EVENTID")
	private String eventID;
	
	@Column(name = "VENID")
	private String venID;
	
	@Column(name = "OPTSTATUS")
	private String optStatus;
	
	@Column(name = "PROGRAM")
	private String program;
	
	@Column(name = "TIME")
	private Date time;
	
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getEventID() {
		return eventID;
	}
	public void setEventID(String eventId) {
		this.eventID = eventId;
	}
	public String getVenID() {
		return venID;
	}
	public void setVenID(String venID) {
		this.venID = venID;
	}
	public String getOptStatus() {
		return optStatus;
	}
	public void setOptStatus(String optStatus) {
		this.optStatus = optStatus;
	}
	public String getProgram() {
		return program;
	}
	public void setProgram(String program) {
		this.program = program;
	}
	
	@Override
	public String toString(){
		return ("\nVEN ID: " + this.venID +
				"\nEvent ID: " + this.eventID +
				"\nProgram: " + this.program + 
				"\nStatus: " + this.optStatus +
				"\nTime: " + this.time.toString());
	}
	public Date getTime() {
		return time;
	}
	public void setTime(Date time) {
		this.time = time;
	}
	public String displayTime(){
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy @ h:mm aa");
		return(dateFormat.format(time));
	}
	
	
}