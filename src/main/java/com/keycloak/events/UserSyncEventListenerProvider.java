package com.keycloak.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public class UserSyncEventListenerProvider
        implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(UserSyncEventListenerProvider.class);

    private static final String REALM_NAME = "Sabeel";
    private static final String PINCODE_FORM_FIELD = "pincode";
    private static final ObjectMapper mapper = new ObjectMapper();



    private final KeycloakSession session;
    private final RealmProvider model;

    public UserSyncEventListenerProvider(KeycloakSession session) {
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
            notifyUserSuppression(event.getUserId());
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {
//
//
//        if(!model.getRealmByName(REALM_NAME).equals(session.getContext().getRealm())) {
//            log.info("Not the right realm");
//            return;
//        }
//
//        log.info("onEvent(AdminEvent)");
//        log.infof("Resource path: %s", adminEvent.getResourcePath());
//        log.infof("Resource type: %s", adminEvent.getResourceType());
//        log.infof("Operation type: %s", adminEvent.getOperationType());
//        log.infof("AdminEvent.toString(): %s", toString(adminEvent));
//
//
//        if (ResourceType.USER.equals(adminEvent.getResourceType())
//                && OperationType.CREATE.equals(adminEvent.getOperationType())) {
//            UserModel user = findUserByAdminEvent(adminEvent);
//            notifyUserCreation(user);
//        }

//        if (ResourceType.USER.equals(adminEvent.getResourceType())
//                && OperationType.DELETE.equals(adminEvent.getOperationType())) {
//            notifyUserDeletion(adminEvent.getResourcePath().substring(6));
//        }
    }


    private UserModel getUser(Event event) {
        RealmModel realm = this.model.getRealm(event.getRealmId());
        return  this.session.users().getUserById(realm, event.getUserId());
    }

    private UserModel findUserByAdminEvent(AdminEvent event) {
        RealmModel realm = this.model.getRealm(event.getRealmId());
        return  this.session.users().getUserById(realm, event.getResourcePath().substring(6));
    }




    private void notifyUserSuppression(String userId) {
        UserPayload payload = new UserPayload();
        payload.setId(userId);
        payload.setAction(Actions.DELETE_ACCOUNT.toString());
        try {
            log.info("Sending request to API with data: " + payload);
            String data = mapper.writeValueAsString(payload);
            RestClient.sendRequest(data);
            log.info("A user has been deleted and post API");
        } catch (Exception e) {
            log.infof("Failed to call API: %s", e);
        }
    }


    private void notifyUserRegistration(UserModel user, String pincode) {

        user.getGroupsStream().forEach(groupModel -> log.infof("Group: %s", groupModel.getName()));
        user.getRealmRoleMappingsStream().forEach(roleModel -> log.infof("Role: %s", roleModel.getName()));
        String profilePicture = Optional.ofNullable(user.getAttributes().get("profilePicture"))
                .map(List::getFirst)
                .orElse(null);
        UserPayload payload = new UserPayload();
        payload.setId(user.getId());
        payload.setEmail(user.getEmail());
        payload.setUsername(user.getUsername());
        payload.setFirstName(user.getFirstName());
        payload.setLastName(user.getLastName());
        payload.setPincode(pincode);
        payload.setPhoneNumber(user.getAttributes().get("phoneNumber").getFirst());
        payload.setProfilePicture(profilePicture);
        payload.setEnabled(user.isEnabled());
        payload.setEmailVerified(user.isEmailVerified());
        payload.setAction(Actions.REGISTER.toString());
        try {
            String data = mapper.writeValueAsString(payload);

            log.info("Sending request to API with data: " + payload);
            RestClient.sendRequest(data);
            log.info("A new user has been registered");
        } catch (Exception e) {
            log.infof("Failed to call API: %s", e);
        }
    }

    private void notifyUserCreation(UserModel user) {
        user.getGroupsStream().forEach(groupModel -> log.infof("Group: %s", groupModel.getName()));
        user.getRealmRoleMappingsStream().forEach(roleModel -> log.infof("Role: %s", roleModel.getName()));
        String data = """
                {
                    "id": "%s",
                    "email": "%s",
                    "userName": "%s",
                    "firstName": "%s",
                    "lastName": "%s",
                    "phoneNumber": "%s",
                    "enabled": %s,
                    "emailVerified": %s,
                    "action": "%s"
                }
                """.formatted(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getAttributes().get("phoneNumber").getFirst(),
                user.isEnabled(),
                user.isEmailVerified(),
                Actions.CREATE);
        try {
            log.info("Sending request to API with data: " + data);
            RestClient.sendRequest(data);
            log.info("A new user has been created");
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