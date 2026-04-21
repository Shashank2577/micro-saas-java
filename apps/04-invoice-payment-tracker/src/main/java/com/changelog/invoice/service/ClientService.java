package com.changelog.invoice.service;

import com.changelog.invoice.domain.Client;
import com.changelog.invoice.repository.ClientRepository;
import com.changelog.model.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.changelog.exception.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Client> listClients(UUID tenantId) {
        return clientRepository.findAllByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public Client getClient(UUID clientId, UUID tenantId) {
        return clientRepository.findByIdAndTenantId(clientId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));
    }

    @Transactional
    public Client createClient(UUID tenantId, Client client) {
        client.setTenant(entityManager.getReference(Tenant.class, tenantId));
        return clientRepository.save(client);
    }

    @Transactional
    public Client updateClient(UUID clientId, UUID tenantId, Client updates) {
        Client existing = getClient(clientId, tenantId);
        existing.setName(updates.getName());
        existing.setCompany(updates.getCompany());
        existing.setEmail(updates.getEmail());
        existing.setAddress(updates.getAddress());
        existing.setPaymentTerms(updates.getPaymentTerms());
        existing.setCurrency(updates.getCurrency());
        return clientRepository.save(existing);
    }

    @Transactional
    public void deleteClient(UUID clientId, UUID tenantId) {
        Client client = getClient(clientId, tenantId);
        clientRepository.delete(client);
    }
}
