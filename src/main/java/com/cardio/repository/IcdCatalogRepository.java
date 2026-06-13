package com.cardio.repository;

import com.cardio.model.IcdCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface IcdCatalogRepository extends JpaRepository<IcdCatalog, String> {

    // Tìm kiếm ICD theo mã hoặc tên bệnh (dùng cho autocomplete)
    @Query("SELECT i FROM IcdCatalog i WHERE " +
           "LOWER(i.icdCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(i.diseaseName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<IcdCatalog> searchByKeyword(String keyword);
}
