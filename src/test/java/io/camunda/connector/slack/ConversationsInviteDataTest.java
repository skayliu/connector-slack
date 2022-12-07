package io.camunda.connector.slack;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsInviteRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.*;
import com.slack.api.model.Conversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConversationsInviteDataTest {
    @Mock
    private MethodsClient methodsClient;
    @Mock private UsersLookupByEmailResponse lookupByEmailResponse;
    @Mock private ConversationsListResponse conversationsListResponse;
    @Mock private User user;
    @Mock private ConversationsInviteResponse conversationsInviteResponse;

    @Captor
    private ArgumentCaptor<ConversationsInviteRequest> conversationsInviteRequest;

    private static final String USERID = "testUserId";

    private static final String CHANNEL_NAME = "channel";

    @Test
    void invoke_shouldThrowExceptionWhenUserNotFoundByEmail() throws SlackApiException, IOException {
        // Given
        ConversationsInviteData conversationsInviteData = new ConversationsInviteData();
        conversationsInviteData.setChannelName(CHANNEL_NAME);
        conversationsInviteData.setUsers("test1@test.com, test2@test.com");
        when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class))).thenReturn(null);
        // When and then
        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> conversationsInviteData.invoke(methodsClient),
                        "RuntimeException was expected");
        assertThat(thrown.getMessage())
                .contains(
                        "Unable to find user with name or email : test1@test.com");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "test1@test.com, test2@test.com",
                    "firstName.LastName1@mail.org,firstName.LastName2@mail.org",
                    "n.a.m.e@mail.ua",
                    "a@m.uat",
                    "_23@ma.au"
            })
    void invoke_shouldFindUserIdByEmail(final String emailList) throws SlackApiException, IOException {
        // Given
        ConversationsInviteData conversationsInviteData = new ConversationsInviteData();
        conversationsInviteData.setUsers(emailList);
        conversationsInviteData.setChannelName(CHANNEL_NAME);

        when(methodsClient.usersLookupByEmail(any(UsersLookupByEmailRequest.class)))
                .thenReturn(lookupByEmailResponse);
        when(lookupByEmailResponse.isOk()).thenReturn(true);
        when(lookupByEmailResponse.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(USERID);

        when(methodsClient.conversationsList(any(ConversationsListRequest.class)))
                .thenReturn(conversationsListResponse);
        when(conversationsListResponse.isOk()).thenReturn(true);
        when(conversationsListResponse.getChannels())
                .thenReturn(Arrays.asList(Conversation.builder().id("wrong_id").name("wrong_name").build(),
                        Conversation.builder().id("good_id").name(CHANNEL_NAME).build()));
        when(conversationsListResponse.getResponseMetadata())
                .thenReturn(new ResponseMetadata());

        when(methodsClient.conversationsInvite(conversationsInviteRequest.capture()))
                .thenReturn(conversationsInviteResponse);
        when(conversationsInviteResponse.isOk()).thenReturn(true);
        when(conversationsInviteResponse.getChannel()).thenReturn(new Conversation());
        when(conversationsInviteResponse.getNeeded()).thenReturn("needed");
        when(conversationsInviteResponse.getProvided()).thenReturn("provided");
        // When
        conversationsInviteData.invoke(methodsClient);
        // Then
        ConversationsInviteRequest value = conversationsInviteRequest.getValue();
        List<String> expectedResolvedUserIds = Arrays.stream(emailList.split(",")).map(s -> USERID).collect(Collectors.toList());

        assertEquals("good_id", value.getChannel());
        assertTrue(value.getUsers().containsAll(expectedResolvedUserIds) && expectedResolvedUserIds.containsAll(value.getUsers()));
    }
}
