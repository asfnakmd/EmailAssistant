package com.emailassistant.email;

import com.emailassistant.state.EmailData;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class EmailReceiverService {

    private static final Logger log = LoggerFactory.getLogger(EmailReceiverService.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String protocol;

    public EmailReceiverService(
            @Value("${spring.mail.imap.host:imap.qq.com}") String host,
            @Value("${spring.mail.imap.port:993}") int port,
            @Value("${spring.mail.username}") String username,
            @Value("${spring.mail.password}") String password,
            @Value("${spring.mail.imap.protocol:imaps}") String protocol) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.protocol = protocol;
    }

    private Store connect() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", port);
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(host, port, username, password);
        log.info("IMAP 已连接到 {}:{}", host, port);
        return store;
    }

    public List<EmailData> fetchUnreadEmails() {
        return fetchEmails("UNSEEN", 20);
    }

    public List<EmailData> fetchRecentEmails(int count) {
        return fetchEmails("ALL", count);
    }

    private List<EmailData> fetchEmails(String searchTerm, int maxCount) {
        List<EmailData> emails = new ArrayList<>();
        Store store = null;
        Folder inbox = null;
        try {
            store = connect();
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Message[] messages;
            if ("UNSEEN".equalsIgnoreCase(searchTerm)) {
                messages = inbox.search(
                        new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false));
            } else {
                int total = inbox.getMessageCount();
                int start = Math.max(1, total - maxCount + 1);
                messages = inbox.getMessages(start, total);
            }

            if (messages.length == 0) {
                log.info("没有找到邮件");
                return emails;
            }

            for (int i = 0; i < messages.length; i++) {
                EmailData email = convertMessage(messages[i]);
                if (email != null) {
                    emails.add(email);
                }
            }

            log.info("获取到 {} 封邮件", emails.size());
        } catch (Exception e) {
            log.error("获取邮件失败: {}", e.getMessage());
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }
        return emails;
    }

    private EmailData convertMessage(Message msg) {
        try {
            String from = "";
            Address[] fromAddresses = msg.getFrom();
            if (fromAddresses != null && fromAddresses.length > 0) {
                from = ((InternetAddress) fromAddresses[0]).getAddress();
            }

            List<String> to = new ArrayList<>();
            Address[] toAddresses = msg.getRecipients(Message.RecipientType.TO);
            if (toAddresses != null) {
                for (Address addr : toAddresses) {
                    to.add(((InternetAddress) addr).getAddress());
                }
            }

            String subject = msg.getSubject();
            if (subject != null) {
                subject = MimeUtility.decodeText(subject);
            }

            String body = extractTextBody(msg);
            String htmlBody = extractHtmlBody(msg);

            LocalDateTime receivedAt = null;
            Date receivedDate = msg.getReceivedDate();
            if (receivedDate == null) {
                receivedDate = msg.getSentDate();
            }
            if (receivedDate != null) {
                receivedAt = LocalDateTime.ofInstant(
                        receivedDate.toInstant(), ZoneId.systemDefault());
            }

            String messageId = msg.getHeader("Message-ID") != null
                    && msg.getHeader("Message-ID").length > 0
                    ? msg.getHeader("Message-ID")[0] : UUID.randomUUID().toString();

            List<EmailData.Attachment> attachments = extractAttachments(msg);

            return new EmailData(
                    messageId, from, to, List.of(),
                    subject != null ? subject : "(无主题)",
                    body != null ? body : "",
                    htmlBody, receivedAt, attachments,
                    null, EmailData.Priority.NORMAL
            );
        } catch (Exception e) {
            log.warn("解析邮件失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractTextBody(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String text = extractTextBody(mp.getBodyPart(i));
                if (text != null) return text;
            }
        }
        return null;
    }

    private String extractHtmlBody(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String html = extractHtmlBody(mp.getBodyPart(i));
                if (html != null) return html;
            }
        }
        return null;
    }

    private List<EmailData.Attachment> extractAttachments(Part part) throws Exception {
        List<EmailData.Attachment> attachments = new ArrayList<>();
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())
                        || bp.getFileName() != null && !bp.isMimeType("text/plain")
                        && !bp.isMimeType("text/html")) {
                    String filename = bp.getFileName();
                    if (filename != null) {
                        filename = MimeUtility.decodeText(filename);
                    }
                    long size = bp.getSize();
                    String mimeType = bp.getContentType();
                    attachments.add(new EmailData.Attachment(
                            filename != null ? filename : "unnamed",
                            mimeType, size));
                }
            }
        }
        return attachments;
    }

    public void markAsRead(String messageId) {
        Store store = null;
        Folder inbox = null;
        try {
            store = connect();
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(
                    new jakarta.mail.search.MessageIDTerm(messageId));
            for (Message msg : messages) {
                msg.setFlag(Flags.Flag.SEEN, true);
            }
            log.info("已标记邮件为已读: {}", messageId);
        } catch (Exception e) {
            log.warn("标记已读失败: {}", e.getMessage());
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }
    }

    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException ignored) {}
        }
    }

    private void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException ignored) {}
        }
    }
}
