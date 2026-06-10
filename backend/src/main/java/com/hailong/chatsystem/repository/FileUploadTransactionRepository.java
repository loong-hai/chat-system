// repository/FileUploadTransactionRepository.java
package com.hailong.chatsystem.repository;

import com.hailong.chatsystem.model.entity.FileUploadTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileUploadTransactionRepository extends JpaRepository<FileUploadTransaction, Long> {

    Optional<FileUploadTransaction> findByFileKey(String fileKey);

    List<FileUploadTransaction> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

    @Modifying
    @Query("UPDATE FileUploadTransaction t SET t.status = :status, t.confirmedAt = :confirmedAt WHERE t.fileKey = :fileKey")
    void updateStatus(@Param("fileKey") String fileKey, @Param("status") String status, @Param("confirmedAt") LocalDateTime confirmedAt);

    @Query("SELECT t FROM FileUploadTransaction t WHERE t.status = 'PENDING' AND t.retryCount < 3 AND t.createdAt < :time")
    List<FileUploadTransaction> findRetryableTransactions(@Param("time") LocalDateTime time);
}