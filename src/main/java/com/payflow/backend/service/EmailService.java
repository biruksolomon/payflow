package com.payflow.backend.service;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;

    @Value("${app.email.from:noreply@payflow.com}")
    private String fromEmail;

    @Value("${app.email.from-name:PayFlow}")
    private String fromName;

    @Value("${app.api.base-url:http://localhost:8080/api}")
    private String baseUrl;

    public void sendEmailVerification(String sendTo, String token) {
        try {
            Optional<User> userOptional = userRepository.findByEmail(sendTo);

            if (userOptional.isEmpty()) {
                log.warn("User not found with email: {}", sendTo);
                return;
            }

            User user = userOptional.get();
            String verificationLink = baseUrl + "/auth/verify-email?email=" + sendTo + "&token=" + token;

            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(sendTo);
            helper.setSubject("Verify Your PayFlow Account - Secure Your Payment Experience");

            String htmlContent = buildEmailVerificationTemplate(user.getFirstName(), verificationLink, token);
            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            log.info("Email verification sent successfully to: {}", sendTo);

        } catch (MessagingException e) {
            log.error("Failed to send email verification to {}: {}", sendTo, e.getMessage(), e);
            throw new RuntimeException("Failed to send verification email", e);
        } catch (Exception e) {
            log.error("Unexpected error sending verification email to {}: {}", sendTo, e.getMessage(), e);
            throw new RuntimeException("Unexpected error while sending verification email", e);
        }
    }

    private String buildEmailVerificationTemplate(String firstName, String verificationLink, String token) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        * {
                            margin: 0;
                            padding: 0;
                            box-sizing: border-box;
                        }
                        
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 20px;
                        }
                        
                        .container {
                            background: white;
                            border-radius: 12px;
                            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
                            max-width: 600px;
                            width: 100%;
                            overflow: hidden;
                        }
                        
                        .header {
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            padding: 40px 20px;
                            text-align: center;
                            color: white;
                        }
                        
                        .header h1 {
                            font-size: 28px;
                            margin-bottom: 10px;
                            font-weight: 700;
                            letter-spacing: 0.5px;
                        }
                        
                        .header p {
                            font-size: 14px;
                            opacity: 0.95;
                            font-weight: 300;
                        }
                        
                        .content {
                            padding: 40px 30px;
                        }
                        
                        .greeting {
                            font-size: 18px;
                            color: #333;
                            margin-bottom: 20px;
                            font-weight: 600;
                        }
                        
                        .message {
                            color: #555;
                            font-size: 15px;
                            line-height: 1.8;
                            margin-bottom: 30px;
                        }
                        
                        .highlight {
                            color: #667eea;
                            font-weight: 600;
                        }
                        
                        .verification-section {
                            background: linear-gradient(135deg, #f5f7ff 0%, #f0f4ff 100%);
                            border-left: 4px solid #667eea;
                            padding: 20px;
                            border-radius: 8px;
                            margin: 30px 0;
                        }
                        
                        .verification-label {
                            font-size: 12px;
                            color: #667eea;
                            font-weight: 700;
                            text-transform: uppercase;
                            letter-spacing: 1px;
                            margin-bottom: 10px;
                        }
                        
                        .verification-code {
                            background: white;
                            padding: 15px;
                            border-radius: 6px;
                            font-family: 'Courier New', monospace;
                            font-size: 16px;
                            font-weight: 700;
                            color: #667eea;
                            text-align: center;
                            letter-spacing: 2px;
                            word-break: break-all;
                        }
                        
                        .cta-button {
                            display: inline-block;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            color: white;
                            padding: 14px 40px;
                            text-decoration: none;
                            border-radius: 8px;
                            font-weight: 600;
                            font-size: 15px;
                            margin: 20px 0;
                            transition: all 0.3s ease;
                            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
                            cursor: pointer;
                            border: none;
                        }
                        
                        .cta-button:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
                        }
                        
                        .cta-container {
                            text-align: center;
                            margin: 30px 0;
                        }
                        
                        .link-text {
                            color: #667eea;
                            word-break: break-all;
                            font-size: 12px;
                            line-height: 1.6;
                        }
                        
                        .security-notice {
                            background: #fff3cd;
                            border-left: 4px solid #ffc107;
                            padding: 15px;
                            border-radius: 6px;
                            margin: 20px 0;
                            font-size: 13px;
                            color: #856404;
                            line-height: 1.6;
                        }
                        
                        .security-icon {
                            display: inline-block;
                            margin-right: 8px;
                        }
                        
                        .features {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 20px;
                            margin: 30px 0;
                        }
                        
                        .feature-item {
                            text-align: center;
                            padding: 15px;
                            background: #f8f9ff;
                            border-radius: 8px;
                            font-size: 13px;
                            color: #666;
                        }
                        
                        .feature-icon {
                            font-size: 24px;
                            margin-bottom: 8px;
                        }
                        
                        .footer {
                            background: #f8f9fa;
                            padding: 30px;
                            border-top: 1px solid #e9ecef;
                            text-align: center;
                        }
                        
                        .footer-links {
                            margin-bottom: 15px;
                            font-size: 12px;
                        }
                        
                        .footer-links a {
                            color: #667eea;
                            text-decoration: none;
                            margin: 0 10px;
                        }
                        
                        .footer-text {
                            color: #999;
                            font-size: 12px;
                            line-height: 1.6;
                        }
                        
                        .copyright {
                            color: #bbb;
                            font-size: 11px;
                            margin-top: 15px;
                            border-top: 1px solid #e9ecef;
                            padding-top: 15px;
                        }
                        
                        .urgency-badge {
                            display: inline-block;
                            background: #ff6b6b;
                            color: white;
                            padding: 4px 12px;
                            border-radius: 20px;
                            font-size: 11px;
                            font-weight: 700;
                            margin-left: 8px;
                            text-transform: uppercase;
                        }
                        
                        @media (max-width: 600px) {
                            .content {
                                padding: 25px 20px;
                            }
                            
                            .header {
                                padding: 30px 20px;
                            }
                            
                            .header h1 {
                                font-size: 24px;
                            }
                            
                            .features {
                                grid-template-columns: 1fr;
                            }
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <!-- Header -->
                        <div class="header">
                            <h1>🔐 PayFlow</h1>
                            <p>Secure Payment Processing Platform</p>
                        </div>
                        
                        <!-- Content -->
                        <div class="content">
                            <div class="greeting">Welcome, <span class="highlight">%s</span>! 👋</div>
                            
                            <div class="message">
                                Thank you for creating your PayFlow account. We're excited to have you on board! To complete your registration and unlock all the powerful payment processing features, please verify your email address by clicking the button below.
                            </div>
                            
                            <!-- Features Preview -->
                            <div class="features">
                                <div class="feature-item">
                                    <div class="feature-icon">⚡</div>
                                    <div>Fast Payments</div>
                                </div>
                                <div class="feature-item">
                                    <div class="feature-icon">🛡️</div>
                                    <div>Secure Transactions</div>
                                </div>
                                <div class="feature-item">
                                    <div class="feature-icon">📊</div>
                                    <div>Analytics & Reports</div>
                                </div>
                                <div class="feature-item">
                                    <div class="feature-icon">🌍</div>
                                    <div>Global Support</div>
                                </div>
                            </div>
                            
                            <!-- CTA Button -->
                            <div class="cta-container">
                                <a href="%s" class="cta-button">Verify Email Address</a>
                            </div>
                            
                            <!-- Verification Code Section -->
                            <div class="verification-section">
                                <div class="verification-label">📋 Verification Token</div>
                                <div class="verification-code">%s</div>
                            </div>
                            
                            <!-- Alternative Link -->
                            <div class="message" style="background: #f8f9ff; padding: 15px; border-radius: 6px; font-size: 13px;">
                                <strong>Can't click the button?</strong> Copy and paste this link in your browser:
                                <div class="link-text" style="margin-top: 10px;">%s</div>
                            </div>
                            
                            <!-- Security Notice -->
                            <div class="security-notice">
                                <span class="security-icon">🔒</span>
                                <strong>Security Note:</strong> This verification link will expire in 24 hours. If you didn't create this account, please ignore this email or contact our support team immediately.
                            </div>
                            
                            <!-- Additional Info -->
                            <div class="message" style="font-size: 14px; color: #777; margin-top: 25px;">
                                Once verified, you'll have full access to:
                                <ul style="margin: 10px 0 0 20px; color: #667eea;">
                                    <li>💳 Advanced payment processing</li>
                                    <li>📈 Real-time transaction monitoring</li>
                                    <li>🔐 Enhanced security features</li>
                                    <li>⚙️ Customizable settings & preferences</li>
                                </ul>
                            </div>
                        </div>
                        
                        <!-- Footer -->
                        <div class="footer">
                            <div class="footer-links">
                                <a href="#">Help Center</a>
                                <a href="#">Contact Support</a>
                                <a href="#">Privacy Policy</a>
                            </div>
                            <div class="footer-text">
                                <p>Have questions? Our support team is here to help.</p>
                                <p>Email: <strong>support@payflow.com</strong></p>
                            </div>
                            <div class="copyright">
                                © 2024 PayFlow. All rights reserved. | Secure Payment Platform
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """
                .replace("{{FIRST_NAME}}", firstName)
                .replace("{{LINK}}", verificationLink)
                .replace("{{TOKEN}}", token);
    }
}
