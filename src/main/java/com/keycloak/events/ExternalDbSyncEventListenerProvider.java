package com.keycloak.events;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;

import java.util.StringJoiner;

public class ExternalDbSyncEventListenerProvider
        implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(ExternalDbSyncEventListenerProvider.class);

    private static final String REALM_NAME = "Sabeel";
    private static final String PINCODE_FORM_FIELD = "pincode";


    private final KeycloakSession session;
    private final RealmProvider model;

    public ExternalDbSyncEventListenerProvider(KeycloakSession session) {
        this.session = session;
        this.model = session.realms();
    }

    @Override
    public void onEvent(Event event) {

        if(!model.getRealmByName(REALM_NAME).equals(session.getContext().getRealm())) {
            log.info("Not the right realm");
            return;
        }

        log.infof("New %s Event", event.getType());
        log.infof("onEvent-> %s", toString(event));
        event.getDetails().forEach((key, value) -> log.infof("%s : %s", key, value));


        if (EventType.REGISTER.equals(event.getType())) {
            log.info("User registered");
            String pincode = session.getContext().getAuthenticationSession().getAuthNote(PINCODE_FORM_FIELD);
            UserModel user = getUser(event);
            notifyUserRegistration(user,pincode);
        }

        if(EventType.DELETE_ACCOUNT.equals(event.getType())) {
            log.info("User account deleted");
            notifyUserDeletion(event.getUserId());
        }

    }



    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {


        if(!model.getRealmByName(REALM_NAME).equals(session.getContext().getRealm())) {
            log.info("Not the right realm");
            return;
        }

        log.info("onEvent(AdminEvent)");
        log.infof("Resource path: %s", adminEvent.getResourcePath());
        log.infof("Resource type: %s", adminEvent.getResourceType());
        log.infof("Operation type: %s", adminEvent.getOperationType());
        log.infof("AdminEvent.toString(): %s", toString(adminEvent));


        if (ResourceType.USER.equals(adminEvent.getResourceType())
                && OperationType.CREATE.equals(adminEvent.getOperationType())) {
            UserModel user = findUserByAdminEvent(adminEvent);
            notifyUserCreation(user);
        }

        if (ResourceType.USER.equals(adminEvent.getResourceType())
                && OperationType.DELETE.equals(adminEvent.getOperationType())) {
            notifyUserDeletion(adminEvent.getResourcePath().substring(6));
        }
    }


    private UserModel getUser(Event event) {
        RealmModel realm = this.model.getRealm(event.getRealmId());
       return  this.session.users().getUserById(realm, event.getUserId());
    }

    private UserModel findUserByAdminEvent(AdminEvent event) {
        RealmModel realm = this.model.getRealm(event.getRealmId());
       return  this.session.users().getUserById(realm, event.getResourcePath().substring(6));
    }


    private void notifyUserDeletion(String userId) {
        String data = """
                {
                    "id": "%s",
                    "email": "%s",
                    "userName": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "action": "%s"
                }
                """.formatted(userId, null, null, null, null, Actions.DELETE);
        try {
            RestClient.sendRequest(data);
            log.info("A user has been deleted and post API");
        } catch (Exception e) {
            log.infof("Failed to call API: %s", e);
        }
    }


    private void notifyUserRegistration(UserModel user, String pincode) {

        String data = """
                {
                    "id": "%s",
                    "email": "%s",
                    "userName": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "action": "%s",
                    "pincode": "%s"
                }
                """.formatted(user.getId(), user.getEmail(), user.getUsername(), user.getFirstName(), user.getLastName(), Actions.CREATE, pincode);
        try {
            RestClient.sendRequest(data);
            log.info("A new user has been registered and post API");
        } catch (Exception e) {
            log.infof("Failed to call API: %s", e);
        }
    }

    private void notifyUserCreation(UserModel user) {
        String data = """
                {
                    "id": "%s",
                    "email": "%s",
                    "userName": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "action": "%s"
                }
                """.formatted(user.getId(), user.getEmail(), user.getUsername(), user.getFirstName(), user.getLastName(),Actions.CREATE);
        try {
            RestClient.sendRequest(data);
            log.info("A new user has been created and post API");
        } catch (Exception e) {
            log.infof("Failed to call API: %s", e);
        }
    }

    @Override
    public void close() {
    }

    private String toString(Event event) {
        StringJoiner joiner = new StringJoiner(", ");

        joiner.add("type=" + event.getType())
                .add("realmId=" + event.getRealmId())
                .add("clientId=" + event.getClientId())
                .add("userId=" + event.getUserId())
                .add("ipAddress=" + event.getIpAddress());

        if (event.getError() != null) {
            joiner.add("error=" + event.getError());
        }

        if (event.getDetails() != null) {
            event.getDetails().forEach((key, value) -> {
                if (value == null || !value.contains(" ")) {
                    joiner.add(key + "=" + value);
                } else {
                    joiner.add(key + "='" + value + "'");
                }
            });
        }

        return joiner.toString();
    }

    private String toString(AdminEvent event) {
        RealmModel realm = this.model.getRealm(event.getRealmId());
        UserModel newRegisteredUser = this.session.users().getUserById(realm, event.getAuthDetails().getUserId());

        StringJoiner joiner = new StringJoiner(", ");

        joiner.add("operationType=" + event.getOperationType())
                .add("realmId=" + event.getAuthDetails().getRealmId())
                .add("clientId=" + event.getAuthDetails().getClientId())
                .add("userId=" + event.getAuthDetails().getUserId());

        if (newRegisteredUser != null) {
            joiner.add("email=" + newRegisteredUser.getEmail())
                    .add("username=" + newRegisteredUser.getUsername())
                    .add("firstName=" + newRegisteredUser.getFirstName())
                    .add("lastName=" + newRegisteredUser.getLastName());
        }

        joiner.add("ipAddress=" + event.getAuthDetails().getIpAddress())
                .add("resourcePath=" + event.getResourcePath());

        if (event.getError() != null) {
            joiner.add("error=" + event.getError());
        }

        return joiner.toString();
    }

}