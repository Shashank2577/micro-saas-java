package com.changelog.business.portals.service;

import com.changelog.business.portals.model.PortalMessage;
import com.changelog.business.portals.repository.PortalMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final PortalMessageRepository messageRepository;

    public List<PortalMessage> getMessages(UUID portalId) {
        return messageRepository.findByPortalIdOrderByCreatedAtAsc(portalId);
    }

    public PortalMessage addMessage(PortalMessage message) {
        return messageRepository.save(message);
    }
}
