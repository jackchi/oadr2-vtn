package service;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.Transactional;
import play.mvc.Result;

import test.*;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;

import org.enernoc.open.oadr2.model.EiCreatedEvent;
import org.enernoc.open.oadr2.model.EiEvent;
import org.enernoc.open.oadr2.model.EiRequestEvent;
import org.enernoc.open.oadr2.model.EiResponse;
import org.enernoc.open.oadr2.model.EventResponses;
import org.enernoc.open.oadr2.model.EventResponses.EventResponse;
import org.enernoc.open.oadr2.model.OadrCreatedEvent;
import org.enernoc.open.oadr2.model.OadrDistributeEvent;
import org.enernoc.open.oadr2.model.OadrDistributeEvent.OadrEvent;
import org.enernoc.open.oadr2.model.OadrRequestEvent;
import org.enernoc.open.oadr2.model.OadrResponse;
import org.enernoc.open.oadr2.model.OptTypeType;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;

public class XmppService {

    static final String OADR2_XMLNS = "http://openadr.org/oadr-2.0a/2012/03";
        
    private ConnectionConfiguration connConfig = new ConnectionConfiguration("msawant-mbp.local", 5222);
    
    private XMPPConnection vtnConnection;
    private XMPPConnection testConnection;
            
    //TODO add these to a config file like spring config or something, hardcoded for now
    private String vtnUsername = "xmpp-vtn";
    private String vtnPassword = "xmpp-pass";
    
    Marshaller marshaller;
    DatatypeFactory xmlDataTypeFac;
    
    static EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("Events");
    static EntityManager entityManager = entityManagerFactory.createEntityManager();
        
    public XmppService() throws XMPPException, InstantiationException, IllegalAccessException, JAXBException{
        //Add for debugging
        //Connection.DEBUG_ENABLED = true;
        vtnConnection = connect(vtnUsername, vtnPassword, "vtn");
        vtnConnection.addPacketListener(oadrPacketListener(), oadrPacketFilter());
        
        //testConnection = connect("test", "xmpp-pass", "vtn");        

        JAXBManager jaxb = new JAXBManager();
        marshaller = jaxb.createMarshaller();
        
    }
    
    public XmppService(String username, String password){
        this.vtnUsername = username;
        this.vtnPassword = password;
    }
   
    @Transactional
    public PacketListener oadrPacketListener(){
        return new PacketListener(){
            @Override
            @Transactional
            public void processPacket(Packet packet){
                OADR2PacketExtension extension = (OADR2PacketExtension)packet.getExtension(OADR2_XMLNS);
                Object packetObject = null;
                try {
                    packetObject = EiEventService.unmarshalRequest(extension.toXML().getBytes());
                } catch (JAXBException e) {}
                if(packetObject instanceof OadrRequestEvent || packetObject instanceof OadrCreatedEvent){
                    if(packetObject instanceof OadrRequestEvent){
                        OadrRequestEvent requestEvent = (OadrRequestEvent)packetObject;
                        EiEventService.onRequestEvent(requestEvent);
                        try {
                            sendXMPPDistribute(requestEvent);
                        } catch (JAXBException e) {Logger.info("JAXBException from sendXMPPDistribute");
                        }
                    }
                    else if(packetObject instanceof OadrCreatedEvent){
                        OadrCreatedEvent createdEvent = (OadrCreatedEvent)packetObject;
                        EiEventService.onCreatedEvent(createdEvent);
                        try {
                            sendXMPPResponse(createdEvent);
                        } catch (JAXBException e) {Logger.info("JAXBException from sendXMPPResponse");
                        }
                    }
                }          
            }
        };
    }
    
    public PacketFilter oadrPacketFilter(){
        return new PacketFilter(){
            @Override
            public boolean accept(Packet packet){
                return packet.getExtension(OADR2_XMLNS) != null;
            }
        };
    }
    
    public void testRequest(){
        OadrRequestEvent ore = new OadrRequestEvent().withEiRequestEvent(new EiRequestEvent().withVenID("test-customer-one").withRequestID("8675309").withReplyLimit((long) 12));
        IQ packet = new OADR2IQ(new OADR2PacketExtension(ore, marshaller));
        packet.setTo("xmpp-vtn@msawant-mbp.local/vtn");
        testConnection.sendPacket(packet);
    }
    
    public XMPPConnection connect(String username, String password, String resource) throws InstantiationException, IllegalAccessException, XMPPException{
       XMPPConnection connection = new XMPPConnection(connConfig);
       if(!connection.isConnected()){
           connection.connect();
           if(connection.getUser() == null){
               connection.login(username, password, resource);
           }
       }
       /*
       Presence presence = new Presence(Presence.Type.available);
       connection.sendPacket(presence);
       */
       return connection;
    }
    
    @Transactional
    public void sendXMPPDistribute(OadrRequestEvent request) throws JAXBException{
        createNewEm();
        String eventId = (String)entityManager.createQuery("SELECT s.eventID FROM StatusObject s WHERE s.venID = :ven")
            .setParameter("ven", request.getEiRequestEvent().getVenID())
            .getSingleResult();
        createNewEm();
        EiEvent event = (EiEvent)entityManager.createQuery("SELECT event FROM EiEvent event, EiEvent$EventDescriptor " +
                "descriptor WHERE descriptor.eventID = :id and event.hjid = descriptor.hjid")
                .setParameter("id", eventId)
                .getSingleResult();
        OadrDistributeEvent distributeEvent = new OadrDistributeEvent().withOadrEvent(new OadrEvent().withEiEvent(event))
                .withEiResponse(new EiResponse().withResponseCode("200"));
        StringWriter out = new StringWriter();
        marshaller.marshal(distributeEvent, out);
        Logger.info(out.toString());
        /*
        OADR2IQ iq = new OADR2IQ(new OADR2PacketExtension(distributeEvent, marshaller));
        iq.setTo("xmpp-ven@msawant-mbp.local/msawant-mbp");
        vtnConnection.sendPacket(iq);
        */
    }
    
    public void sendXMPPResponse(OadrCreatedEvent createdEvent) throws JAXBException{
        OadrResponse response = new OadrResponse();
        response.withEiResponse(new EiResponse().withRequestID(createdEvent.getEiCreatedEvent().getEiResponse().getRequestID())
                .withResponseCode("200"));
        StringWriter out = new StringWriter();
        marshaller.marshal(response, out);
        Logger.info(out.toString());
    }
    
    public static void createNewEm(){
        entityManager = entityManagerFactory.createEntityManager();
        if(!entityManager.getTransaction().isActive()){
            entityManager.getTransaction().begin();
        }
    }
    
}