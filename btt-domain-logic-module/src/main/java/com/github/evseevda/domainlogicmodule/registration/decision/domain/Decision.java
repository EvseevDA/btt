package com.github.evseevda.domainlogicmodule.registration.decision.domain;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionTypeConverter;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Редактируемое до согласования решение по заявлению перевозчика. */
@Entity
@Getter
@Table(name = "decision")
public class Decision {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Заявление, по которому подготовлено решение. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private TransporterRequest request;

    /** Исполнитель, подготовивший решение. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by_id", nullable = false)
    private UserAccount decidedBy;

    /** Вид заявления или решения. */
    @Convert(converter = DecisionTypeConverter.class)
    @Column(name = "type", nullable = false)
    private DecisionType type;

    /** Комментарий к решению. */
    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    /** Текущее состояние записи. */
    @Convert(converter = DecisionStatusConverter.class)
    @Column(name = "status", nullable = false)
    private DecisionStatus status;

    /** Время итогового решения в UTC. */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Пользователь, согласовавший решение. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private UserAccount approvedBy;

    /** Время согласования в UTC. */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Причина возврата на доработку. */
    @Column(name = "return_reason", columnDefinition = "text")
    private String returnReason;

    /** Описание изменений. */
    @Column(name = "change_description", columnDefinition = "text")
    private String changeDescription;

    /** Номер редакции решения. */
    @Column(name = "revision", nullable = false)
    private int revision;

    protected Decision() {
    }

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public Decision(
            UUID id,
            TransporterRequest request,
            UserAccount decidedBy,
            DecisionType type,
            String comment,
            String changeDescription
    ) {
        this.id = id;
        this.request = request;
        this.decidedBy = decidedBy;
        this.type = type;
        this.comment = comment;
        this.changeDescription = changeDescription;
        status = DecisionStatus.DRAFT;
        revision = 1;
    }

    /** Сохраняет следующую редакцию проекта решения. */
    public void revise(UserAccount actor, DecisionType verdict, String text, String description) {
        decidedBy = actor;
        type = verdict;
        comment = text;
        changeDescription = description;
        status = DecisionStatus.DRAFT;
        revision++;
    }

    /** Записывает причину возврата на доработку. */
    public void recordReturn(String reason) {
        returnReason = reason;
    }

    /** Фиксирует согласовавшего и дату итогового решения. */
    public void approve(UserAccount reviewer, LocalDateTime time) {
        approvedBy = reviewer;
        approvedAt = time;
        decidedAt = time;
        status = DecisionStatus.ISSUED;
    }
}
