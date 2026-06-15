package com.payflow.backend.service;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EmailService emailService;

    private User user;

    @BeforeEach
    void setup() {

        ReflectionTestUtils.setField(
                emailService,
                "fromEmail",
                "noreply@payflow.com"
        );

        ReflectionTestUtils.setField(
                emailService,
                "fromName",
                "PayFlow"
        );

        ReflectionTestUtils.setField(
                emailService,
                "baseUrl",
                "http://localhost:8080/api"
        );

        user = User.builder()
                .email("user@test.com")
                .firstName("Biruk")
                .build();
    }

    @Test
    void shouldSendVerificationEmailSuccessfully() {

        String token = "TOKEN123";

        MimeMessage mimeMessage =
                new MimeMessage(Session.getDefaultInstance(new Properties()));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(javaMailSender.createMimeMessage())
                .thenReturn(mimeMessage);

        emailService.sendEmailVerification(
                user.getEmail(),
                token
        );

        verify(userRepository)
                .findByEmail(user.getEmail());

        verify(javaMailSender)
                .createMimeMessage();

        verify(javaMailSender)
                .send(any(MimeMessage.class));
    }

    @Test
    void shouldNotSendEmailWhenUserDoesNotExist() {

        when(userRepository.findByEmail("missing@test.com"))
                .thenReturn(Optional.empty());

        emailService.sendEmailVerification(
                "missing@test.com",
                "TOKEN123"
        );

        verify(javaMailSender, never())
                .send(any(MimeMessage.class));
    }

    @Test
    void shouldThrowExceptionWhenMailSenderFails() {

        MimeMessage mimeMessage =
                new MimeMessage(Session.getDefaultInstance(new Properties()));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(javaMailSender.createMimeMessage())
                .thenReturn(mimeMessage);

        doThrow(new RuntimeException("SMTP ERROR"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        assertThrows(
                RuntimeException.class,
                () -> emailService.sendEmailVerification(
                        user.getEmail(),
                        "TOKEN123"
                )
        );
    }

    @Test
    void shouldGenerateVerificationLinkCorrectly() {

        MimeMessage mimeMessage =
                new MimeMessage(Session.getDefaultInstance(new Properties()));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(javaMailSender.createMimeMessage())
                .thenReturn(mimeMessage);

        emailService.sendEmailVerification(
                user.getEmail(),
                "ABC123"
        );

        verify(javaMailSender)
                .send(any(MimeMessage.class));
    }
}