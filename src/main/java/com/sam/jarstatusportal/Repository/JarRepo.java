package com.sam.jarstatusportal.Repository;

import com.sam.jarstatusportal.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JarRepo extends JpaRepository<User, Long> {

    @Query(value = "SELECT * FROM user_list WHERE id=?1", nativeQuery = true)
    List<User> findByyId(Long id);

}
