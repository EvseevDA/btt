package com.github.evseevda.domainlogicmodule.identity.service;

import com.github.evseevda.domainlogic.model.AccountSummary;
import com.github.evseevda.domainlogic.model.OrganizationMemberDto;
import com.github.evseevda.domainlogic.model.SessionDto;
import com.github.evseevda.domainlogic.model.SessionOrganizationDto;
import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.identity.repository.AccountRepository;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembership;
import com.github.evseevda.domainlogicmodule.organization.repository.OrganizationMembershipRepository;
import com.github.evseevda.domainlogicmodule.organization.repository.OrganizationRepository;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganization;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/** Предоставляет кабинету права текущего аккаунта и доступные ему справочники сотрудников. */
@Service
@Profile("!mock-api")
@Transactional(readOnly = true)
public class SessionService {
    private final AccountRepository accounts;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;

    /** Подключает аккаунты и членства организаций. */
    public SessionService(AccountRepository accounts, OrganizationRepository organizations, OrganizationMembershipRepository memberships) {
        this.accounts = accounts;
        this.organizations = organizations;
        this.memberships = memberships;
    }

    /** Возвращает права и организации текущего аккаунта. */
    public SessionDto session() {
        var id = CurrentAccount.id();
        var user = accounts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var membershipsByOrganization = memberships.findAllByIdUserId(id).stream()
                .collect(toMap(member -> member.getId().organizationId(), Function.identity()));
        var entries = organizations.findAllById(membershipsByOrganization.keySet()).stream()
                .sorted(Comparator.comparing(TransporterOrganization::getName).thenComparing(TransporterOrganization::getId))
                .map(organization -> {
                    var member = membershipsByOrganization.get(organization.getId());
                    return new SessionOrganizationDto(organization.getId(), organization.getName(),
                            member.isCanRepresent(), member.isCanConfirmMembers());
                }).toList();
        return new SessionDto(summary(user), CurrentAccount.permissions(), entries);
    }

    /** Возвращает участников организации для представителя, управляющего членством. */
    public List<OrganizationMemberDto> members(UUID organization) {
        boolean allowed = memberships.findById(new OrganizationMembership.Key(organization, CurrentAccount.id()))
                .filter(OrganizationMembership::isCanConfirmMembers).isPresent();
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var entries = memberships.findAllByIdOrganizationId(organization).stream()
                .collect(Collectors.toMap(member -> member.getId().userId(), Function.identity()));
        return accounts.findAllById(entries.keySet()).stream()
                .sorted(Comparator.comparing(UserAccount::getFullName).thenComparing(UserAccount::getId))
                .map(account -> {
                    var member = entries.get(account.getId());
                    return new OrganizationMemberDto(summary(account), member.isCanRepresent(), member.isCanConfirmMembers());
                }).toList();
    }

    /** Возвращает сотрудников, которым можно назначить заявление указанного процесса. */
    public List<AccountSummary> staff(String permission) {
        if (!permission.matches("transporter\\.(registration|amendment|carrier-cancellation|admin-cancellation)\\.decide")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестное разрешение исполнителя");
        }
        CurrentAccount.require(permission);
        CurrentAccount.require(permission.substring(0, permission.lastIndexOf('.') + 1) + "assign");
        return accounts.findStaffWithPermission(permission).stream().map(SessionService::summary).toList();
    }

    private static AccountSummary summary(UserAccount account) {
        return new AccountSummary(account.getId(), account.getFullName());
    }
}
