package ru.debugger4o4.userscountertelegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.debugger4o4.userscountertelegrambot.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByChatId(Long chatId);

    User findByUsername(String username);
}
