package com.company;

import java.sql.*;
import java.util.Scanner;

// Programm 2 Osterferien Projekt Mathias Angerer
public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Connection connection = null;
        try {
            String url = "jdbc:mysql://localhost:3306/program1?user=root";
            connection = DriverManager.getConnection(url);

            // getting customer account number, new customers create new account
            int customerId = customer1(connection);

            // printing menu
            printMenu(connection);

            // choosing of meals
            String choice = chooseMeal();
            int orderId = 0;

            // adding meal to DB
            if (!choice.equalsIgnoreCase("end")) {
                orderId = createOrder(connection, customerId, choice);
                createOrderDetailTable(connection, orderId);
                addMealToOrderDetailTable(connection, choice, orderId);
                addMealTotal(connection, choice);
            }
            while (!choice.equalsIgnoreCase("end")) {
                printMenu(connection);
                choice = chooseMeal();
                if (!choice.equalsIgnoreCase("end")) {
                    updatePrice(connection, choice, orderId);
                    addMealToOrderDetailTable(connection, choice, orderId);
                    addMealTotal(connection, choice);
                }
            }

            // printing of ingredients
            printMealsIngredients(connection, orderId);
            String userAction = "yes";
            // if ingredients want to be changed, changing ingredients
            while (userAction.equalsIgnoreCase("yes")) {
                System.out.println("do you want to change ingredients? (yes) or (no)");
                userAction = scanner.nextLine();
                if (userAction.equalsIgnoreCase("yes")) changeIngredient(connection, orderId);
            }

            // printing of bill
            bill(connection, orderId, customerId);
        } catch (SQLException e) {
            throw new Error("connection problem", e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    public static void addMealTotal(Connection connection, String meal) {
        final String createAccount = "INSERT INTO `program1`.`meals_total` (meal)" +
                "Values (?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(createAccount)) {
            preparedStatement.setString(1, meal);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            System.out.println("addMealTotal problem");
        }
    }

    public static void bill(Connection connection, int orderId, int customerId) {
        System.out.println("bill from order: " + orderId + " customerNr: " + customerId);
        double priceMeals = printBillMeal(connection, orderId);
        double priceDelivery = printBillDeliveryPrice(connection, orderId);
        double priceExtra = printBillExtraPrice(connection, orderId);
        double discount = (discount(connection, customerId) * priceMeals);
        double totalPrice = ((priceMeals - discount) + priceDelivery + priceExtra);
        System.out.println("discount              - " + discount + "$");
        System.out.println("________________________________");
        System.out.println("total                     " + totalPrice + "$");
    }

    public static double discount(Connection connection, int customerId) {
        double dicount = 0;
        int count = 0;
        final String discountQuery = "SELECT order_id FROM `program1`.`order` WHERE customer_id = '" + customerId + "'";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(discountQuery);
            while (resultSet.next()) {
                count++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("dicount price problem");
        }
        if (count >= 20) {
            dicount = 0.2;
            System.out.println("discount                20%");
        } else if (count >= 10) {
            dicount = 0.1;
            System.out.println("discount                10%");
        }
        return dicount;
    }

    public static double printBillExtraPrice(Connection connection, int orderId) {
        double price = 0;
        final String extraQuery = "SELECT extra_price FROM `program1`.`order` WHERE order_id = '" + orderId + "'";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(extraQuery);
            while (resultSet.next()) {
                double extraPrice = resultSet.getDouble("extra_price");
                System.out.println("extra                   " + extraPrice + "$");
                price += extraPrice;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("printBillExtraPrice price problem");
        }
        return price;
    }

    public static double printBillDeliveryPrice(Connection connection, int orderId) {
        double price = 0;
        final String deliveryQuery = "SELECT delivery_price FROM `program1`.`city_price` WHERE city_id = " +
                "(SELECT delivery_price FROM `program1`.`order` WHERE order_id = '" + orderId + "')";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(deliveryQuery);
            while (resultSet.next()) {
                double deliveryPrice = resultSet.getDouble("delivery_price");
                System.out.println("delivery               " + deliveryPrice + "$");
                price += deliveryPrice;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("printBillDeliveryPrice price problem");
        }
        return price;
    }

    public static double printBillMeal(Connection connection, int orderId) {
        double price = 0;
        final String mealQuery = "SELECT * FROM `order_details`.`order_" + orderId + "_detail`";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(mealQuery);
            while (resultSet.next()) {
                String meal = resultSet.getString("meal");
                double priceMeal = resultSet.getInt("price");
                System.out.println(meal + "                   " + priceMeal + "$");
                price += priceMeal;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("printMealsIngredients price problem");
        }
        return price;
    }

    public static void changeIngredient(Connection connection, int orderId) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("type in (meal) where you wanna change an ingredient");
        String mealToChange = scanner.nextLine();
        printIngredient(mealToChange, connection);
        System.out.println("choose (ingredient)");
        String ingredient = scanner.nextLine();
        System.out.println("type in (remove) or (replace)");
        String action = scanner.nextLine();
        String newIngredient = "";
        if (action.equalsIgnoreCase("replace")) {
            System.out.println("type in (replacement ingredient)");
            newIngredient = scanner.nextLine();
        }
        if (action.equalsIgnoreCase("remove")) newIngredient = null;
        ingredientChangeToDB(connection, orderId, ingredient, newIngredient, mealToChange);
    }

    public static void ingredientChangeToDB(Connection connection, int orderId, String oldIngredient,
                                            String newIngredient, String meal) {
        createOrderChangeTable(connection, orderId);
        final String SQL_INSERT = "INSERT INTO `order_changes`.`order_" + orderId + "_change` (meal, ingredientRemove," +
                "ingredientAdd)" +
                "Values (?,?,?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
            preparedStatement.setString(1, meal);
            preparedStatement.setString(2, oldIngredient);
            preparedStatement.setString(3, newIngredient);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
            System.out.println("ingredientChangeToDB problem");
        }
        addExtraPriceToOrder(connection, orderId);
    }

    public static void addExtraPriceToOrder(Connection connection, int orderId) {
        final String SQL_Update = "UPDATE `order`" +
                "SET extra_price = 2.5 " +
                "WHERE order_id = '" + orderId + "';";
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_Update);
        } catch (SQLException ex) {
            System.out.println("update extraPrice problem");
        }
    }

    public static void printMealsIngredients(Connection connection, int orderId) {

        System.out.println("your order and ingredients:");
        final String mealQuery = "SELECT meal FROM `order_details`.`order_" + orderId + "_detail`";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(mealQuery);
            while (resultSet.next()) {
                String meal = resultSet.getString("meal");
                printIngredient(meal, connection);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("printMealsIngredients price problem");
        }
    }

    public static void addMealToOrderDetailTable(Connection connection, String meal, int orderId) {
        double price = 0;
        final String priceQuery = "SELECT price FROM `program1`.`menu` WHERE `menu`.`menu_name` = '" + meal + "'";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(priceQuery);
            resultSet.next();
            price = resultSet.getDouble("price");
        } catch (SQLException e) {
            System.out.println("get menu price problem");
        }

        final String SQL_INSERT = "INSERT INTO `order_details`.`order_" + orderId + "_detail` (meal, price)" +
                "Values (?,?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
            preparedStatement.setString(1, meal);
            preparedStatement.setDouble(2, price);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void updatePrice(Connection connection, String choice, int orderId) {
        final String SQL_Update = "UPDATE `order`" +
                "SET total_meal_price = total_meal_price + (SELECT price FROM `menu` WHERE menu_name = '" + choice + "')" +
                "WHERE order_id = '" + orderId + "';";
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_Update);
        } catch (SQLException ex) {
            System.out.println("update Price problem");
        }
    }

    public static void createOrderChangeTable(Connection connection, int orderId) {
        final String SQL_Table = "CREATE TABLE `order_changes`.`order_" + orderId + "_change`" +
                "( `meal` VARCHAR(50) NOT NULL, `ingredientRemove` VARCHAR(50) NOT NULL, `ingredientAdd` VARCHAR(50)) ENGINE = InnoDB;";
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_Table);
        } catch (SQLException ex) {
            ex.printStackTrace();
            System.out.println("createOrderChangeTable problem");
        }
    }

    public static void createOrderDetailTable(Connection connection, int orderId) {
        final String SQL_Table = "CREATE TABLE `order_details`.`order_" + orderId + "_detail`" +
                "( `meal` VARCHAR(50) NOT NULL , `price` DOUBLE NOT NULL ) ENGINE = InnoDB;";
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_Table);
        } catch (SQLException ex) {
            ex.printStackTrace();
            System.out.println("createOrderDetailTable problem");
        }
    }

    public static int createOrder(Connection connection, int customerId, String choice) {
        int orderId = 0;
        final String SQL_INSERT = "INSERT INTO `order` (`order_id`, `customer_id`, `delivery_price`, `total_meal_price`)\n" +
                "VALUES (NULL, '" + customerId + "' , (SELECT customer_city_id FROM customer " +
                "WHERE customer_id = '" + customerId + "'),\n" +
                "        (SELECT price FROM menu WHERE menu_name = '" + choice + "'));";
        try (Statement statement = connection.createStatement()) {
            statement.execute(SQL_INSERT);
        } catch (SQLException ex) {
            ex.printStackTrace();
            System.out.println("createOrder problem");
        }
        final String query = "SELECT MAX(order_id) AS order_id FROM `order`";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(query);
            if (resultSet.next()) orderId = resultSet.getInt("order_id");
        } catch (SQLException e) {
            System.out.println("createOrder, read order id problem");
        }
        return orderId;
    }


    public static void printMenu(Connection connection) {
        final String printMenu = "SELECT menu_name from menu";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(printMenu);
            System.out.println("current menu: ");
            while (resultSet.next()) {
                System.out.println("-" + resultSet.getString("menu_name"));
            }
        } catch (SQLException e) {
            System.out.println("menu print problem");
        }
    }

    public static String chooseMeal() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("type in your (meal of choice) or (end)");
        return scanner.nextLine();
    }

    public static void printIngredient(String menu, Connection connection) {
        System.out.println("ingredients of " + menu + ":");
        for (int i = 1; i <= 10; i++) {
            String printIngredient = "SELECT ingredients.ingredient_name\n" +
                    "FROM menu\n" +
                    "INNER JOIN ingredients ON menu.ingredient" + i + " = ingredients.ingredient_id\n" +
                    "WHERE menu_name = '" + menu + "';";
            try (Statement statementRead = connection.createStatement()) {
                ResultSet resultSet = statementRead.executeQuery(printIngredient);
                String ingredient = "";
                while (resultSet.next()) {
                    ingredient = resultSet.getString(1);
                }
                if (!ingredient.equalsIgnoreCase("")) {
                    System.out.print("-" + ingredient);
                }
            } catch (SQLException e) {
                System.out.println("ingredient print problem");
            }
        }
        System.out.println("\n");
    }

    public static int customer1(Connection connection) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("insert (account number) or insert (0) to create an account");
        int accountNumber = scanner.nextInt();
        if (accountNumber == 0) {
            signUp(connection);
            System.out.println("insert account number:");
            accountNumber = scanner.nextInt();
        }
        return accountNumber;
    }

    public static void signUp(Connection connection) {
        int accountNumber = 0;
        int cityId = 0;
        Scanner scanner = new Scanner(System.in);
        System.out.println("insert your name:");
        String customerName = scanner.nextLine();
        System.out.println("insert your city:");
        String customerCity = scanner.nextLine();
        final String cityQuery = "SELECT city_id from city_price WHERE city_name = '" + customerCity + "';";
        try (Statement statementCity = connection.createStatement()) {
            ResultSet resultSet = statementCity.executeQuery(cityQuery);
            if (resultSet.next()) cityId = resultSet.getInt("city_id");
        } catch (SQLException e) {
            System.out.println("searching city_id problem");
        }
        final String createAccount = "INSERT INTO customer (customer_name, customer_city_id)" +
                "Values (?,?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(createAccount)) {
            preparedStatement.setString(1, customerName);
            preparedStatement.setInt(2, cityId);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            System.out.println("signUp problem");
        }
        final String accountNumberQuery = "SELECT customer_id from customer WHERE customer_name = '" + customerName +
                "' AND customer_city_id = '" + cityId + "';";
        try (Statement statementRead = connection.createStatement()) {
            ResultSet resultSet = statementRead.executeQuery(accountNumberQuery);
            if (resultSet.next()) accountNumber = resultSet.getInt("customer_id");
            System.out.println("account created\naccount number: " + accountNumber);
        } catch (SQLException e) {
            System.out.println("searching account number problem");
        }
    }
}
