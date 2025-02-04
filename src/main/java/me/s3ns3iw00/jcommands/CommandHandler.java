/*
 * Copyright (C) 2022 S3nS3IW00
 *
 * This file is part of JCommands.
 *
 * JCommands is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser general Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * JCommands is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package me.s3ns3iw00.jcommands;

import me.s3ns3iw00.jcommands.argument.Argument;
import me.s3ns3iw00.jcommands.argument.ArgumentResult;
import me.s3ns3iw00.jcommands.argument.InputArgument;
import me.s3ns3iw00.jcommands.argument.SubArgument;
import me.s3ns3iw00.jcommands.argument.ability.Autocompletable;
import me.s3ns3iw00.jcommands.argument.autocomplete.AutocompleteState;
import me.s3ns3iw00.jcommands.argument.concatenation.Concatenator;
import me.s3ns3iw00.jcommands.argument.converter.ArgumentResultConverter;
import me.s3ns3iw00.jcommands.argument.converter.type.URLConverter;
import me.s3ns3iw00.jcommands.argument.type.ComboArgument;
import me.s3ns3iw00.jcommands.argument.util.Choice;
import me.s3ns3iw00.jcommands.builder.CommandBuilder;
import me.s3ns3iw00.jcommands.event.type.ArgumentMismatchEvent;
import me.s3ns3iw00.jcommands.event.type.BadCategoryEvent;
import me.s3ns3iw00.jcommands.event.type.BadChannelEvent;
import me.s3ns3iw00.jcommands.event.type.CommandActionEvent;
import me.s3ns3iw00.jcommands.limitation.Limitation;
import me.s3ns3iw00.jcommands.limitation.type.CategoryLimitable;
import me.s3ns3iw00.jcommands.limitation.type.ChannelLimitable;
import me.s3ns3iw00.jcommands.limitation.type.RoleLimitable;
import me.s3ns3iw00.jcommands.limitation.type.UserLimitable;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.interaction.*;

import java.net.URL;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The main class of the API
 *
 * @author S3nS3IW00
 */
public class CommandHandler {

    /**
     * Some cool and obvious stuff here
     */
    private static DiscordApi api;
    private static final List<Command> commands = new ArrayList<>();
    private static final Map<Server, List<Command>> serverCommands = new HashMap<>();

    /**
     * HasMap that contains the converters initiated with default converters
     */
    private static final Map<Class<?>, ArgumentResultConverter> converters = new HashMap<Class<?>, ArgumentResultConverter>() {{
        put(URL.class, new URLConverter());
    }};

    /**
     * Initiates the command listener
     *
     * @param api is the instance of the Discord api
     */
    public static void setApi(DiscordApi api) {
        CommandHandler.api = api;
        api.addSlashCommandCreateListener(event -> handleCommand(event.getSlashCommandInteraction()));
        api.addAutocompleteCreateListener(event -> handleAutocomplete(event.getAutocompleteInteraction()));
    }

    /**
     * Handles slash command interactions
     *
     * @param interaction the slash command interaction
     */
    private static void handleCommand(SlashCommandInteraction interaction) {
        User sender = interaction.getUser();
        Optional<TextChannel> channel = interaction.getChannel();
        Optional<Command> commandOptional = commands.stream()
                .filter(c -> c.getName().equalsIgnoreCase(interaction.getCommandName()))
                .findFirst();

        // Check that the command needs to be handled by JCommands
        commandOptional.ifPresent(command -> {
            //Category and channel validation
            if (channel.isPresent() && channel.get().getType() == ChannelType.SERVER_TEXT_CHANNEL) {
                Optional<ChannelCategory> category = channel.get().asServerTextChannel().get().getCategory();
                if (category.isPresent()) {
                    if (command instanceof CategoryLimitable) {
                        CategoryLimitable categoryLimitable = (CategoryLimitable) command;
                        if ((categoryLimitable.getCategoryLimitations().stream().anyMatch(Limitation::isPermit) && categoryLimitable.getCategoryLimitations().stream().noneMatch(l -> l.getEntity().getId() == category.get().getId())) ||
                                (categoryLimitable.getCategoryLimitations().stream().noneMatch(Limitation::isPermit) && categoryLimitable.getCategoryLimitations().stream().anyMatch(l -> l.getEntity().getId() == category.get().getId()))) {
                            categoryLimitable.getBadCategoryListener()
                                    .ifPresent(listener -> listener.onBadCategory(new BadCategoryEvent(command, sender, new CommandResponder(interaction), category.get())));
                            return;
                        }
                    }
                }
                if (command instanceof ChannelLimitable) {
                    ChannelLimitable channelLimitable = (ChannelLimitable) command;
                    if ((channelLimitable.getChannelLimitations().stream().anyMatch(Limitation::isPermit) && channelLimitable.getChannelLimitations().stream().noneMatch(l -> l.getEntity().getId() == channel.get().getId())) ||
                            (channelLimitable.getChannelLimitations().stream().noneMatch(Limitation::isPermit) && channelLimitable.getChannelLimitations().stream().anyMatch(l -> l.getEntity().getId() == channel.get().getId()))) {
                        channelLimitable.getBadChannelListener().ifPresent(listener -> listener.onBadChannel(new BadChannelEvent(command, sender, new CommandResponder(interaction), channel.get())));
                        return;
                    }
                }
            }

            //Argument validation
            Optional<Map<Argument, ArgumentResult>> resultOptional = processArguments(interaction, command.getArguments(), interaction.getOptions());

            if (resultOptional.isPresent()) {
                /* -- Argument concatenation --
                   The results list stores the result of the arguments that the concatenation process will overwrite with the concatenated values
                   The concatenated map caches the finished concatenation's process result to be able to use that in the next concatenation if present
                 */
                List<ArgumentResult> results = new LinkedList<>(resultOptional.get().values());
                Map<ArgumentResult, List<Argument>> concatenated = new LinkedHashMap<>();
                for (Concatenator concatenator : command.getConcatenators().keySet()) {
                    /* Get arguments that need to be concatenated
                       Except the optional arguments that haven't been specified
                     */
                    List<Argument> concatenatedArguments = command.getConcatenators().get(concatenator).stream()
                            .filter(arg -> !(arg instanceof InputArgument) ||
                                    !((InputArgument) arg).isOptional() ||
                                    (((InputArgument) arg).isOptional() &&
                                            resultOptional.get().containsKey(arg)))
                            .collect(Collectors.toCollection(LinkedList::new));

                    // Checks whether an argument's result is exist (so it has a value) or it is optional (so it does not need to have a value)
                    Predicate<Argument> argumentExistenceChecker = arg -> resultOptional.get().containsKey(arg) ||
                            (arg instanceof InputArgument) && ((InputArgument) arg).isOptional();
                    // Concatenating if the result contains all the arguments in the concatenator or if the argument is optional
                    if (concatenatedArguments.stream().allMatch(argumentExistenceChecker)) {
                        /* Replaces the results with the already concatenated ones in the list that belongs to arguments that have been concatenated before
                           That means if there is multiple concatenation and the concatenator uses arguments that have been concatenated before,
                           then it should use the concatenated value instead of the arguments' value
                         */
                        List<ArgumentResult> concatenateResults = concatenatedArguments.stream()
                                .map(arg -> resultOptional.get().get(arg))
                                .collect(Collectors.toCollection(LinkedList::new));
                        for (ArgumentResult concatenatedResult : concatenated.keySet()) {
                            List<Argument> alreadyConcatenatedArguments = concatenated.get(concatenatedResult);
                            if (concatenatedArguments.containsAll(alreadyConcatenatedArguments)) {
                                alreadyConcatenatedArguments.forEach(arg -> concatenateResults.remove(resultOptional.get().get(arg)));
                                concatenateResults.add(concatenatedArguments.indexOf(alreadyConcatenatedArguments.get(0)), concatenatedResult);
                            }
                        }

                        /* Runs the concatenating process and adds its result to the results at the index of the first argument in the concatenation process
                           Replaces the concatenated arguments to the result of the concatenation
                         */
                        ArgumentResult result = new ArgumentResult(concatenator.getResultType(),
                                concatenator.concatenate(concatenateResults.toArray(new ArgumentResult[0])));
                        results.add(results.indexOf(concatenateResults.get(0)), result);
                        concatenateResults.forEach(results::remove);
                        concatenated.put(result, concatenatedArguments);
                    } else if (concatenatedArguments.stream().anyMatch(argumentExistenceChecker)) {
                        /* Occurs when not all the required argument's value exist in the result but there is at least one that does
                           That means there is at least one argument that has not been registered on the command or not in the same scope as the other arguments in the concatenation process
                           When an argument is in different scope as the others that could be because the argument belongs to different SUB_COMMAND than the others,
                           and for that the arguments cannot be concatenated because its value is missing
                        */
                        throw new IllegalStateException("All non-optional arguments need to belong to the same group and have a value in the same concatenation process! Maybe an arguments is not registered?");
                    }
                }

                command.getActionListener().ifPresent(listener -> listener.onAction(new CommandActionEvent(command, sender,
                        new CommandResponder(interaction), channel.orElse(null), results.toArray(new ArgumentResult[0]))));
            }
        });
    }

    /**
     * Handles autocomplete requests for arguments
     *
     * @param interaction the interaction
     */
    private static void handleAutocomplete(AutocompleteInteraction interaction) {
        Optional<Command> commandOptional = commands.stream()
                .filter(c -> c.getName().equalsIgnoreCase(interaction.getCommandName()))
                .findFirst();

        commandOptional.ifPresent(command -> {
            SlashCommandInteractionOption option = interaction.getFocusedOption();

            User sender = interaction.getUser();
            Optional<TextChannel> channel = interaction.getChannel();
            List<Argument> allArguments = collectArguments(command.getArguments());
            Optional<Argument> argumentOptional = allArguments.stream()
                    .filter(arg -> arg.getName().equalsIgnoreCase(option.getName()))
                    .findFirst();
            argumentOptional.ifPresent(argument -> {
                if (argument instanceof Autocompletable) {
                    Autocompletable autocompletable = (Autocompletable) argument;
                    AutocompleteState autocompleteState = new AutocompleteState(
                            command,
                            channel.orElse(null),
                            sender,
                            argument,
                            getOptionValue(option, argument.getType()),
                            interaction.getArguments().stream()
                                    .collect(Collectors.toMap(
                                            key -> allArguments.stream()
                                                    .filter(arg -> arg.getName().equalsIgnoreCase(key.getName()))
                                                    .findFirst().orElse(null),
                                            value -> value
                                    ))
                                    .entrySet().stream()
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            value -> getOptionValue(value.getValue(), value.getKey().getType())
                                    )));

                    /* Collect results from autocompletes and construct the list */
                    List<Choice> choices = autocompletable.getAutocompletes().stream()
                            .map(autocomplete -> autocomplete.getResult(autocompleteState))
                            .filter(Objects::nonNull)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());

                    if (choices.size() > 0) {
                        interaction.respondWithChoices(choices.stream().map(Choice::getChoice).collect(Collectors.toList()));
                    }
                }
            });
        });
    }

    /**
     * Processes arguments and every argument of the arguments recursively:
     * - Adjusts values to the arguments
     * - Checks that the value is valid for the argument
     * - Assembles a list with the result of every argument
     *
     * @param arguments the list of arguments need to be processed
     * @param options   the list of {@link SlashCommandInteractionOption} corresponding to {@code arguments} parameter
     * @return an {@link Optional} that is empty when one option found that is not valid for the argument during the validating process,
     * otherwise it contains a {@link LinkedHashMap} with {@link Argument}s and their {@link ArgumentResult} in it that converts values to the final result
     */
    private static Optional<Map<Argument, ArgumentResult>> processArguments(SlashCommandInteraction interaction, List<Argument> arguments, List<SlashCommandInteractionOption> options) {
        Map<Argument, ArgumentResult> results = new LinkedHashMap<>();
        for (Argument argument : arguments) {
            // Get the argument that has the same name as the option;
            // Option is null when the argument is marked as optional, and was not specified
            SlashCommandInteractionOption option = options.stream().filter(opt -> opt.getName().equalsIgnoreCase(argument.getName())).findFirst().orElse(null);

            if (option != null) {
                Object value = getOptionValue(option, argument.getType());

                if (argument instanceof SubArgument) {
                    /* Add the result of the argument to the list, which is basically the name of the argument;
                       If the result is present that means there are other arguments that need to be added to the list,
                            otherwise just return an empty optional to tell the caller that there aren't any other options;
                       The best practice is to solve this recursively since the other option's count is unknown,
                            and it cannot be determined directly
                    */
                    results.put(argument, new ArgumentResult(argument));
                    Optional<Map<Argument, ArgumentResult>> result = processArguments(interaction, ((SubArgument) argument).getArguments(), option.getOptions());
                    if (result.isPresent()) {
                        results.putAll(result.get());
                    } else {
                        return Optional.empty();
                    }
                } else if (argument instanceof ComboArgument) {
                    /* Choose the value that the user picked
                       Checking is unnecessary since the user only can pick a valid value
                     */
                    ComboArgument ca = (ComboArgument) argument;

                    ca.choose(value);
                    results.put(argument, new ArgumentResult(ca));
                } else if (argument instanceof InputArgument) {
                    /* Adjusts the value to the argument and checks that the value is null
                       If it is not then it will be added to the list,
                            otherwise return an empty optional to tell the caller that one of the argument is not valid
                     */
                    InputArgument ia = (InputArgument) argument;
                    ia.input(value);

                    if (ia.getValue() != null) {
                        results.put(argument, new ArgumentResult(ia));
                    } else {
                        commands.stream()
                                .filter(cmd -> cmd.getName().equalsIgnoreCase(interaction.getCommandName()))
                                .findFirst()
                                .ifPresent(cmd -> argument.getMismatchListener()
                                        .ifPresent(listener -> listener.onArgumentMismatch(new ArgumentMismatchEvent(cmd, interaction.getUser(), new CommandResponder(interaction), argument))));
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.of(results);
    }

    /**
     * Gets the value of the option based on its type
     *
     * @param option the option
     * @param type   the requested type
     * @return the value of the option based on its type
     */
    private static Object getOptionValue(SlashCommandInteractionOption option, SlashCommandOptionType type) {
        Object value;
        switch (type) {
            case ROLE:
                value = option.getRoleValue().orElse(null);
                break;
            case USER:
                value = option.getUserValue().orElse(null);
                break;
            case CHANNEL:
                value = option.getChannelValue().orElse(null);
                break;
            case MENTIONABLE:
                value = option.getMentionableValue().orElse(null);
                break;
            case STRING:
                value = option.getStringValue().orElse(null);
                break;
            case LONG:
                value = option.getLongValue().orElse(null);
                break;
            case DECIMAL:
                value = option.getDecimalValue().orElse(null);
                break;
            case BOOLEAN:
                value = option.getBooleanValue().orElse(null);
                break;
            default:
                value = null;
        }
        return value;
    }

    /**
     * Returns the {@link Argument} of {@link Command} by {@link SlashCommandInteractionOption}
     *
     * @param command the command
     * @param option  the option
     * @return an {@link Optional} with the {@link Argument} in it if found,
     * otherwise an empty {@link Optional}
     */
    private static Optional<Argument> getArgumentByOption(Command command, SlashCommandInteractionOption option) {
        return collectArguments(command.getArguments()).stream()
                .filter(arg -> arg.getName().equalsIgnoreCase(option.getName()))
                .findFirst();
    }

    /**
     * Collects all arguments of a list of arguments in one list
     * For collecting a command's all arguments {@link Command#getArguments()} need to be passed as parameter
     *
     * @param arguments a list of argument
     * @return the collection
     */
    private static List<Argument> collectArguments(List<Argument> arguments) {
        List<Argument> allArguments = new ArrayList<>();
        for (Argument argument : arguments) {
            if (argument instanceof SubArgument) {
                allArguments.addAll(collectArguments(((SubArgument) argument).getArguments()));
            } else {
                allArguments.add(argument);
            }
        }

        return allArguments;
    }

    /**
     * Registers the command for the listener on the specified servers
     * and sets up permissions with discord's default permission system
     *
     * @param command the command
     * @param servers the list of the servers where the command will be registered
     */
    public static void registerCommand(Command command, Server... servers) {
        commands.add(command);

        /*
          Check if default permissions need to be turned off
          They will get turned off when user or role limitation has been set on a command
        */
        boolean defPermissions = !((command instanceof UserLimitable && ((UserLimitable) command).getUserLimitations().stream().anyMatch(Limitation::isPermit)) ||
                (command instanceof RoleLimitable && ((RoleLimitable) command).getRoleLimitations().stream().anyMatch(Limitation::isPermit)));

        for (Server server : servers) {
            if (!serverCommands.containsKey(server)) {
                serverCommands.put(server, new ArrayList<>());
            }
            serverCommands.get(server).add(command);

            /* Check if the command with the name is already registered
               If it is, then just update it, otherwise register it
            */
            Optional<SlashCommand> slashCommandOptional = api.getServerSlashCommands(server).join().stream().filter(s -> s.getName().equalsIgnoreCase(command.getName())).findAny();
            long id = slashCommandOptional.map(slashCommand -> new SlashCommandUpdater(slashCommand.getId())
                    .setName(command.getName())
                    .setDescription(command.getDescription())
                    .setDefaultPermission(defPermissions)
                    .setSlashCommandOptions(command.getArguments().stream().map(Argument::getCommandOption).collect(Collectors.toList()))
                    .updateForServer(server)
                    .join()
                    .getId()).orElseGet(() -> SlashCommand.with(command.getName(), command.getDescription(), command.getArguments().stream().map(Argument::getCommandOption).collect(Collectors.toList()))
                    .setDefaultPermission(defPermissions)
                    .createForServer(server)
                    .join()
                    .getId());

            applyPermissions(command, id, server);
        }
    }

    /**
     * Registers command globally
     * That means the command will be available in all the servers where the bot on, and also in private
     * Sets up permissions with discord's default permission system
     *
     * @param command the command to register
     */
    public static void registerCommand(Command command) {
        commands.add(command);

        /*
          Check if default permissions need to be turned off
          They will get turned off when user or role limitation has been set on a command
        */
        boolean defPermissions = !((command instanceof UserLimitable && ((UserLimitable) command).getUserLimitations().stream().anyMatch(Limitation::isPermit)) ||
                (command instanceof RoleLimitable && ((RoleLimitable) command).getRoleLimitations().stream().anyMatch(Limitation::isPermit)));

        /* Check if the command with the name is already registered
           If it is, then just update it, otherwise register it
         */
        Optional<SlashCommand> slashCommandOptional = api.getGlobalSlashCommands().join().stream().filter(s -> s.getName().equalsIgnoreCase(command.getName())).findAny();
        long id = slashCommandOptional.map(slashCommand -> new SlashCommandUpdater(slashCommand.getId())
                .setName(command.getName())
                .setDescription(command.getDescription())
                .setDefaultPermission(defPermissions)
                .setSlashCommandOptions(command.getArguments().stream().map(Argument::getCommandOption).collect(Collectors.toList()))
                .updateGlobal(api)
                .join()
                .getId()).orElseGet(() -> SlashCommand.with(command.getName(), command.getDescription(), command.getArguments().stream().map(Argument::getCommandOption).collect(Collectors.toList()))
                .setDefaultPermission(defPermissions)
                .createGlobal(api)
                .join()
                .getId());

        for (Server server : api.getServers()) {
            applyPermissions(command, id, server);
        }
    }

    /**
     * Applies default Discord permissions on command
     *
     * @param command the command
     * @param id      the slash command's id
     * @param server  the server
     */
    private static void applyPermissions(Command command, long id, Server server) {
        List<ApplicationCommandPermissions> permissions = new ArrayList<>();
        if (command instanceof UserLimitable) {
            UserLimitable userLimitable = (UserLimitable) command;
            permissions.addAll(userLimitable.getUserLimitations().stream()
                    .filter(user -> user.getServer().getId() == server.getId())
                    .map(user -> ApplicationCommandPermissions.create(user.getEntity().getId(),
                            ApplicationCommandPermissionType.USER,
                            user.isPermit()))
                    .collect(Collectors.toList()));
        }
        if (command instanceof RoleLimitable) {
            RoleLimitable roleLimitable = (RoleLimitable) command;
            permissions.addAll(roleLimitable.getRoleLimitations().stream()
                    .filter(user -> user.getServer().getId() == server.getId())
                    .map(role -> ApplicationCommandPermissions.create(role.getEntity().getId(),
                            ApplicationCommandPermissionType.ROLE,
                            role.isPermit()))
                    .collect(Collectors.toList()));
        }

        if (permissions.size() > 0) {
            new ApplicationCommandPermissionsUpdater(server)
                    .setPermissions(permissions)
                    .update(id)
                    .join();
        }
    }

    /**
     * Calls the {@link CommandHandler#registerCommand(Command, Server...)} method with the command contained by the {@code CommandBuilder} class
     *
     * @param builder the builder
     * @param servers the list of the servers where the command will be registered
     */
    public static void registerCommand(CommandBuilder<?> builder, Server... servers) {
        registerCommand(builder.getCommand(), servers);
    }

    /**
     * Calls the {@link CommandHandler#registerCommand(Command)} method with the command contained by the {@code CommandBuilder} class
     *
     * @param builder the builder
     */
    public static void registerCommand(CommandBuilder<?> builder) {
        registerCommand(builder.getCommand());
    }

    /**
     * @return the list of the commands
     */
    public static List<Command> getCommands() {
        return commands;
    }

    /**
     * @param server server
     * @return the list of the commands on the specified server
     */
    public static List<Command> getCommands(Server server) {
        return serverCommands.get(server);
    }

    /**
     * Determines that a command with the given name is already exist or not
     *
     * @param cmd the command's name
     * @return exist or not
     */
    public static boolean isCommandExist(String cmd) {
        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(cmd)) return true;
        }
        return false;
    }

    /**
     * Determines that a command with the given name is already exist or not on the specified server
     *
     * @param server server
     * @param cmd    the command's name
     * @return exist or not
     */
    public static boolean isCommandExist(Server server, String cmd) {
        for (Command command : serverCommands.get(server)) {
            if (command.getName().equalsIgnoreCase(cmd)) return true;
        }
        return false;
    }

    /**
     * Registers a converter for the given type
     *
     * @param clazz     the class of the type
     * @param converter the converter
     */
    public static void registerArgumentConverter(Class<?> clazz, ArgumentResultConverter converter) {
        converters.put(clazz, converter);
    }

    /**
     * Gets the registered converter by the type
     *
     * @param clazz the class of the type
     * @return an {@code Optional} that is empty when there is no registered converter for the given type otherwise with value of the converter
     */
    public static Optional<ArgumentResultConverter> getArgumentConverter(Class<?> clazz) {
        if (converters.containsKey(clazz)) {
            return Optional.of(converters.get(clazz));
        }
        return Optional.empty();
    }

    /**
     * @return the discord api
     */
    public static DiscordApi getApi() {
        return api;
    }
}