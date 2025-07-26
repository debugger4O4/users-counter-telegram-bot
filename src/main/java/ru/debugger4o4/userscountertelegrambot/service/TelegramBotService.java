package ru.debugger4o4.userscountertelegrambot.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.debugger4o4.userscountertelegrambot.entity.User;
import ru.debugger4o4.userscountertelegrambot.repository.UserRepository;
import ru.debugger4o4.userscountertelegrambot.util.Util;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private static final String START = "/start";
    private static final String MY_DATA = "Мои данные";
    private static final String LIST_USERS = "Список пользователей";
    private static final String ADD_ADMIN = "Добавить администратора";
    private static final String STATISTIC = "Статистика по пользователям";

    // Флаг для опроса с по имени.
    private boolean name = false;
    // Флаг для опроса с по возрасту.
    private boolean age = false;
    // Флаг для обавления админа.
    private boolean addAdmin = false;
    // Флаг рекурсии при невалидном введении данных по возрасту.
    private boolean firstInvokeAgeQuestion = false;

    @Value("${bot.name}")
    private String telegramBotName;

    private UserRepository userRepository;

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public void setNamedParameterJdbcTemplate(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Autowired
    public void setParishionerRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    public TelegramBotService(@Value("${bot.token}") String botToken) {
        super(botToken);
        List<BotCommand> menu = new ArrayList<>();
        menu.add(new BotCommand("/start", "Зарегистрироваться и пройти опрос."));
        try {
            this.execute(new SetMyCommands(menu, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return telegramBotName;
    }

    @Override
    public void onRegister() {
        super.onRegister();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            User user = userRepository.findByChatId(chatId);
            if (user == null) {
                if (!message.equals("/start")) {
                    // Если пользователя нет в БД, происходит хронологический порядок действий далее помеченный комментарием с номером.
                    String parishionerUserName = update.getMessage().getChat().getUserName();
                    // 1-ое действие.
                    startCommand(chatId, parishionerUserName);
                    // 2-ое действие.
                    registrationCommand(update);
                }
            } else {
                updateKeyboardIfNeeded(chatId, user.getRole(), message);
            }
            switch (message) {
                case START -> {
                    String parishionerUserName = update.getMessage().getChat().getUserName();
                    startCommand(chatId, parishionerUserName);
                    registrationCommand(update);
                }
                case STATISTIC -> {
                    if (user != null && user.getRole().equals("admin")) {
                        generateChart(chatId);
                    } else {
                        sendMessage(chatId, "Эту команду может выполнить только администратор.");
                    }
                }
                case LIST_USERS -> {
                    if (user != null && user.getRole().equals("admin")) {
                        getListUsers(chatId);
                    } else {
                        sendMessage(chatId, "Эту команду может выполнить только администратор.");
                    }
                }
                case ADD_ADMIN -> {
                    if (user != null && user.getRole().equals("admin")) {
                        addAdmin(chatId);
                    } else {
                        sendMessage(chatId, "Эту команду может выполнить только администратор.");
                    }
                }
                case MY_DATA -> getMyData(chatId);
                default -> {
                    if (name && user != null) {
                        getNameAndSaveParishioner(chatId, message);
                    }
                    if (age && user != null) {
                        getAgeAndSaveParishioner(chatId, message);
                    }
                    if (addAdmin) {
                        setAdminRole(chatId, message);
                    }
                }
            }
        }
    }


    private void updateKeyboardIfNeeded(Long chatId, String role, String text) {
        ReplyKeyboardMarkup keyboard = getKeyboard(role);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выполняю команду: '" + text + "'");
        message.setReplyMarkup(keyboard);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error in updateKeyboardIfNeeded: {}", e.getMessage());

        }
    }

    private ReplyKeyboardMarkup getKeyboard(String role) {
        if ("admin".equals(role)) {
            return sendAdminMenu();
        } else {
            return sendUserMenu();
        }
    }

        private ReplyKeyboardMarkup sendAdminMenu () {
            ReplyKeyboardMarkup adminKeyboard = new ReplyKeyboardMarkup();
            adminKeyboard.setSelective(true);
            adminKeyboard.setResizeKeyboard(true);
            adminKeyboard.setOneTimeKeyboard(false);
            List<KeyboardRow> keyboardRows = new ArrayList<>();
            KeyboardRow row1 = new KeyboardRow();
            row1.add(new KeyboardButton(LIST_USERS));
            row1.add(new KeyboardButton(ADD_ADMIN));
            keyboardRows.add(row1);
            KeyboardRow row2 = new KeyboardRow();
            row2.add(new KeyboardButton(STATISTIC));
            row2.add(new KeyboardButton(MY_DATA));
            keyboardRows.add(row2);
            adminKeyboard.setKeyboard(keyboardRows);
            return adminKeyboard;
        }

        private ReplyKeyboardMarkup sendUserMenu () {
            ReplyKeyboardMarkup userKeyboard = new ReplyKeyboardMarkup();
            userKeyboard.setSelective(true);
            userKeyboard.setResizeKeyboard(true);
            userKeyboard.setOneTimeKeyboard(false);
            List<KeyboardRow> keyboardRows = new ArrayList<>();
            KeyboardRow row1 = new KeyboardRow();
            row1.add(new KeyboardButton(MY_DATA));
            keyboardRows.add(row1);
            userKeyboard.setKeyboard(keyboardRows);
            return userKeyboard;
        }

        private void getListUsers (Long chatId){
            List<User> users = userRepository.findAll();
            StringBuilder sb = new StringBuilder();
            for (User user : users) {
                sb
                        .append("\n---------------------------\n")
                        .append("ID: ")
                        .append(user.getId())
                        .append(", Ник: ").append("@").append(user.getUsername())
                        .append(", Имя: ").append(user.getName())
                        .append(", Возраст: ").append(user.getAge())
                        .append(", Роль: ").append(user.getRole())
                        .append("\n---------------------------\n");
            }
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(sb.toString().trim());
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                logger.error("Error in getListUsers: {}", e.getMessage());
            }
        }

        private void addAdmin (Long chatId){
            sendMessage(chatId, "Введите ник кандидата:");
            addAdmin = true;
        }

        private void getMyData (Long chatId){
            User user = userRepository.findByChatId(chatId);
            String sb = "ID: " +
                    user.getId() + "\n" +
                    "Ник: " + "@" + user.getUsername() + "\n" +
                    "Имя: " + user.getName() + "\n" +
                    "Возраст: " + user.getAge() + "\n" +
                    "Роль: " + user.getRole() + "\n";
            sendMessage(chatId, "Ваши данные:\n" + sb);
        }

        private void startCommand (Long chatId, String parishionerName){
            String text = """
                    Добро пожаловать в телеграм-бот анализа регистрации пользователей, %s!
                    """;
            String formattedText = String.format(text, parishionerName);
            sendMessage(chatId, formattedText);
        }

        private void registrationCommand (Update update){
            Long chatId = update.getMessage().getChatId();
            User user = userRepository.findByChatId(chatId);
            if (user == null) {
                user = new User();
                user.setChatId(chatId);
                user.setDateOfRegistration(LocalDateTime.now());
                user.setRole("user");
                user.setUsername(update.getMessage().getFrom().getUserName());
                userRepository.saveAndFlush(user);
            }
            sendGenderQuestion(chatId, user);
        }

        private void sendGenderQuestion (Long chatId, User user){
            if (user.getMale() == null || user.getMale().isEmpty()) {
                SendMessage message = new SendMessage(chatId.toString(), "Выберите ваш пол:");
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> rowMale = new ArrayList<>();
                InlineKeyboardButton maleButton = new InlineKeyboardButton("Мужчина");
                maleButton.setCallbackData("М");
                rowMale.add(maleButton);
                List<InlineKeyboardButton> rowFemale = new ArrayList<>();
                InlineKeyboardButton femaleButton = new InlineKeyboardButton("Женщина");
                femaleButton.setCallbackData("Ж");
                rowFemale.add(femaleButton);
                rows.add(rowMale);
                rows.add(rowFemale);
                markup.setKeyboard(rows);
                message.setReplyMarkup(markup);
                try {
                    executeAsync(message);
                } catch (TelegramApiException e) {
                    logger.error("Error sendGenderQuestion: {}", e.getMessage());
                }
            } else {
                sendMessage(chatId, "Вы уже вводили пол.");
                sendNameQuestion(chatId, user);
            }
        }

        private void sendNameQuestion (Long chatId, User user){
            if (user.getName() == null || user.getName().isEmpty()) {
                SendMessage message = new SendMessage(chatId.toString(), "Введите Ваше имя (ФИО) пожалуйста:");
                try {
                    executeAsync(message);
                    name = true;
                } catch (TelegramApiException e) {
                    logger.error("Error sendNameQuestion: {}", e.getMessage());
                }
            } else {
                sendMessage(chatId, "Вы уже вводили имя.");
                sendAgeQuestion(chatId, user);
            }
        }

        private void getNameAndSaveParishioner (Long chatId, String text){
            User user = userRepository.findByChatId(chatId);
            if (user.getName() == null || user.getName().isEmpty()) {
                if (!text.isEmpty() && text.matches("^[А-ЯЁа-яёA-Za-z ]+$")) {
                    name = false;
                    user.setName(text);
                    userRepository.saveAndFlush(user);
                    // 4-ое действие.
                    sendAgeQuestion(chatId, user);
                } else {
                    sendMessage(chatId, "Имя не может содержать цифры и символы.");
                }
            } else {
                sendMessage(chatId, "Вы уже вводили имя.");
                sendAgeQuestion(chatId, user);
            }
        }

        private void sendAgeQuestion (Long chatId, User user){
            if (user.getAge() == null || user.getAge().isEmpty()) {
                SendMessage message = new SendMessage(chatId.toString(), "Введите Ваш возраст пожалуйста:");
                try {
                    executeAsync(message);
                    age = true;
                } catch (TelegramApiException e) {
                    logger.error("Error sendAgeQuestion: {}", e.getMessage());
                }
            } else {
                sendMessage(chatId, "Вы уже вводили возраст.");
            }
        }

        private void getAgeAndSaveParishioner (Long chatId, String text){
            User user = userRepository.findByChatId(chatId);
            if (user.getAge() == null || user.getAge().isEmpty()) {
                if (!text.isEmpty() && text.matches("\\d+") && text.length() < 4) {
                    age = false;
                    user.setAge(text);
                    userRepository.saveAndFlush(user);
                } else {
                    firstInvokeAgeQuestion = true;
                    if (firstInvokeAgeQuestion) {
                        sendMessage(chatId, "Возраст не может содержать буквы, символы и иметь четырехзначное число.");
                        firstInvokeAgeQuestion = false;
                    }
                }
            } else {
                sendMessage(chatId, "Вы уже вводили возраст.");
            }
        }

        private void completeCommand (Long chatId){
            SendMessage message = new SendMessage(chatId.toString(), "Благодарю за пройденный опрос!");
            try {
                executeAsync(message);
            } catch (TelegramApiException e) {
                logger.error("Error completeCommand: {}", e.getMessage());
            }
        }

         private void sendMessage (Long chatId, String text){
            String chatIdStr = String.valueOf(chatId);
            SendMessage sendMessage = new SendMessage(chatIdStr, text);
            try {
                executeAsync(sendMessage);
            } catch (TelegramApiException e) {
                logger.error("Error sendMessage: {}", e.getMessage());
            }
        }

        private void generateChart ( long chatId){
            String query = "select\n" +
                    "to_char(date_of_registration, 'YYYY-MM') as месяц_регистрации,\n" +
                    "floor(avg(age::integer)) as средний_возраст,\n" +
                    "round(count(*) filter (where male = 'М') :: numeric / count(*) * 100, 2) as доля_мужчин,\n" +
                    "round(count(*) filter (where male = 'Ж') :: numeric / count(*) * 100, 2) as доля_женщин\n" +
                    "from parishioners\n" +
                    "where\n" +
                    "date_of_registration is not null and\n" +
                    "male is not null and\n" +
                    "age is not null and\n" +
                    "frequency_of_church_attendance is not null\n" +
                    "group by месяц_регистрации\n" +
                    "order by месяц_регистрации asc;";
            List<Map<String, Object>> data = namedParameterJdbcTemplate.queryForList(query, new HashMap<>());
            List<String> labels = data.stream()
                    .map(map -> "'" + map.get("месяц_регистрации") + "'")
                    .distinct()
                    .toList();
            List<String> men = Util.getPercentValueFromData(data, "доля_мужчин");
            List<String> women = Util.getPercentValueFromData(data, "доля_женщин");
            List<Integer> age = Util.getAvgAgeFromData(data);
            try {
                String diagramUrl = "https://quickchart.io/chart?c={type:'bar',data:{labels:"
                        + labels + ",datasets:["
                        + "{label:'Мужчины %',data:" + men + ",type:'line',borderWidth:3,lineTension:0.4,showLine:true,datalabels:{display:false},fill:false},"
                        + "{label:'Женщины %',data:" + women + ",type:'line',borderWidth:3,lineTension:0.4,showLine:true,datalabels:{display:false},fill:false},"
                        + "{label:'Средний возраст (лет)',data:" + age + "},"
                        + "]}," +
                        "options:{" +
                        "scales:{" +
                        "yAxes:[{" +
                        "ticks:{beginAtZero:true}," +
                        "scaleLabel:{display:true,labelString:'Проценты'}}]}," +
                        "plugins:{" +
                        "datalabels:{" +
                        "anchor:'center'," +
                        "align:'center'," +
                        "color:'black'," +
                        "font:{size:'8',weight:'bold'}}}," +
                        "title:{display:true,text:'Статистика по прихожанам'}}}";
                InputFile photo = new InputFile(String.valueOf(new URL(diagramUrl)));
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId);
                sendPhoto.setPhoto(photo);
                execute(sendPhoto);
            } catch (IOException | TelegramApiException e) {
                logger.error("Error generate chart: {}", e.getMessage());
            }
        }

        private void handleCallbackQuery (Update update){
            String callbackData = update.getCallbackQuery().getData();
            CallbackQuery callbackQuery = update.getCallbackQuery();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
            answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
            answerCallbackQuery.setShowAlert(false);
            try {
                execute(answerCallbackQuery);
            } catch (TelegramApiException e) {
                logger.error("Error in handleCallbackQuery: {}", e.getMessage());
            }
            if ("М".equals(callbackData) || "Ж".equals(callbackData)) {
                User user = userRepository.findByChatId(chatId);
                user.setMale(callbackData);
                userRepository.saveAndFlush(user);
                // 3-ее действие.
                sendNameQuestion(chatId, user);
                // 5-ое действие. действие
                completeCommand(chatId);
            }
        }

        public void setAdminRole (Long chatId, String username){
            addAdmin = false;
            username = username.replaceAll("@", "");
            User user = userRepository.findByUsername(username);
            if (user != null) {
                user.setRole("admin");
                userRepository.saveAndFlush(user);
            } else {
                sendMessage(chatId, "Нет такого прихожанина.");
            }
        }
    }
