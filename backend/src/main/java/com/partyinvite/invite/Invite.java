package com.partyinvite.invite;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invites")
public class Invite {
    public enum Status { PENDING, ACCEPTED, CONFIRMED, PAID, EXPIRED }

    @Id private String id;
    @Column(nullable=false, length=100) private String eventName;
    @Column(nullable=false, length=1000) private String message;
    @Column(nullable=false) private long amountPaise;
    @Column(nullable=false, length=100) private String hostName;
    @Column(nullable=false, length=254) private String hostEmail;
    @Column(nullable=false, length=100) private String guestName;
    @Column(nullable=false, length=254) private String guestEmail;
    private String photoFile;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="invite_locations", joinColumns=@JoinColumn(name="invite_id"))
    @Column(name="location_name", nullable=false, length=160)
    private List<String> locations = new ArrayList<>();
    @Column(length=160) private String chosenLocation;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Status status = Status.PENDING;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant expiresAt;
    private String orderId;
    private String paymentId;
    @Column(nullable=false) private boolean acceptanceEmailSent = false;

    protected Invite() {}
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getEventName(){return eventName;} public void setEventName(String v){eventName=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public long getAmountPaise(){return amountPaise;} public void setAmountPaise(long v){amountPaise=v;}
    public String getHostName(){return hostName;} public void setHostName(String v){hostName=v;}
    public String getHostEmail(){return hostEmail;} public void setHostEmail(String v){hostEmail=v;}
    public String getGuestName(){return guestName;} public void setGuestName(String v){guestName=v;}
    public String getGuestEmail(){return guestEmail;} public void setGuestEmail(String v){guestEmail=v;}
    public String getPhotoFile(){return photoFile;} public void setPhotoFile(String v){photoFile=v;}
    public List<String> getLocations(){return locations;} public void setLocations(List<String> v){locations=v;}
    public String getChosenLocation(){return chosenLocation;} public void setChosenLocation(String v){chosenLocation=v;}
    public Status getStatus(){return status;} public void setStatus(Status v){status=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
    public String getOrderId(){return orderId;} public void setOrderId(String v){orderId=v;}
    public String getPaymentId(){return paymentId;} public void setPaymentId(String v){paymentId=v;}
    public boolean isAcceptanceEmailSent(){return acceptanceEmailSent;} public void setAcceptanceEmailSent(boolean v){acceptanceEmailSent=v;}
}
