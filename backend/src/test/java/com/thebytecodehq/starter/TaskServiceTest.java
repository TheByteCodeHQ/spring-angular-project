package com.thebytecodehq.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thebytecodehq.starter.task.Task;
import com.thebytecodehq.starter.task.TaskRepository;
import com.thebytecodehq.starter.task.TaskStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class TaskServiceTest {

    @Autowired TaskRepository repository;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void theDatabaseDetectsALostUpdateEvenWhenTheApplicationCheckIsRacedPast() {
        UUID id = repository.saveAndFlush(new Task("Race me", null, TaskStatus.TODO, null)).getId();
        var outer = new TransactionTemplate(transactions);
        var inner = new TransactionTemplate(transactions);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            Task seenByFirstWriter = repository.findById(id).orElseThrow();
            // a second writer commits a change while the first still holds the old version
            inner.executeWithoutResult(s -> repository.findById(id).orElseThrow().changeStatus(TaskStatus.DONE));
            seenByFirstWriter.changeStatus(TaskStatus.IN_PROGRESS);
            repository.flush();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(TaskStatus.DONE);   // the first commit won
    }

    @Test
    void auditFieldsAreFilledInAndRowsGetUniqueIds() {
        Task a = repository.saveAndFlush(new Task("A one", null, TaskStatus.TODO, null));
        Task b = repository.saveAndFlush(new Task("B one", null, TaskStatus.TODO, null));
        assertThat(a.getId()).isNotEqualTo(b.getId());
        assertThat(a.getCreatedAt()).isNotNull();
        assertThat(a.getUpdatedAt()).isNotNull();
        assertThat(a.getCreatedBy()).isEqualTo("system");   // no authenticated user in this test
        assertThat(a.getVersion()).isZero();
    }
}
