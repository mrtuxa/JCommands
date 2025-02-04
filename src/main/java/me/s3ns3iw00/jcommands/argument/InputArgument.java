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
package me.s3ns3iw00.jcommands.argument;

import me.s3ns3iw00.jcommands.argument.ability.Autocompletable;
import me.s3ns3iw00.jcommands.argument.ability.Optionality;
import me.s3ns3iw00.jcommands.argument.autocomplete.Autocomplete;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents argument that can have multiple value depends on the user input and the restrictions of the argument
 * These arguments can be optional
 */
public abstract class InputArgument extends Argument implements Optionality, Autocompletable {

    private Object input;
    private final Class<?> resultType;
    private boolean optional = false;

    private final List<Autocomplete> autocompletes = new ArrayList<>();

    /**
     * Constructs the argument with the default requirements
     *
     * @param name        the argument's name
     * @param description the argument's description
     * @param type        the type of the input value
     */
    public InputArgument(String name, String description, SlashCommandOptionType type) {
        super(name, description, type);

        Class<?> resultType = Object.class;
        switch (type) {
            case STRING:
                resultType = String.class;
                break;
            case LONG:
                resultType = Long.class;
                break;
            case BOOLEAN:
                resultType = Boolean.class;
                break;
            case CHANNEL:
                resultType = TextChannel.class;
                break;
            case ROLE:
                resultType = Role.class;
                break;
            case USER:
                resultType = User.class;
                break;
        }
        this.resultType = resultType;
    }

    /**
     * Runs the default constructor and specifies the result type of the value, that the input will be converted to
     *
     * @param name        the argument's name
     * @param description the argument's description
     * @param type        the type of the value
     * @param resultType  the type of the converted value
     */
    public InputArgument(String name, String description, SlashCommandOptionType type, Class<?> resultType) {
        super(name, description, type);
        this.resultType = resultType;
    }

    @Override
    public SlashCommandOption getCommandOption() {
        return new SlashCommandOptionBuilder()
                .setName(getName())
                .setDescription(getDescription())
                .setType(getType())
                .setRequired(!isOptional())
                .setAutocompletable(autocompletes.size() > 0)
                .build();
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    /**
     * Sets the argument optional
     * Only the last argument of the options can be set as optional
     */
    public void setOptional() {
        optional = true;
    }

    public List<Autocomplete> getAutocompletes() {
        return autocompletes;
    }

    /**
     * Sets the input
     *
     * @param input the input
     */
    public void input(Object input) {
        this.input = input;
    }

    @Override
    public Object getValue() {
        return input;
    }

    @Override
    public Class<?> getResultType() {
        return resultType;
    }

}
