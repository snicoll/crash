/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.crsh.lang.groovy.command;

import groovy.lang.Binding;
import groovy.lang.Closure;
import org.crsh.cli.descriptor.CommandDescriptor;
import org.crsh.cli.impl.descriptor.HelpDescriptor;
import org.crsh.cli.impl.invocation.InvocationMatch;
import org.crsh.cli.impl.lang.CommandFactory;
import org.crsh.cli.impl.lang.InvocationContext;
import org.crsh.cli.spi.Completer;
import org.crsh.command.CommandContext;
import org.crsh.command.CommandCreationException;
import org.crsh.shell.impl.command.spi.Command;
import org.crsh.shell.impl.command.spi.CommandInvoker;
import org.crsh.command.InvocationContextImpl;
import org.crsh.command.RuntimeContext;
import org.crsh.shell.impl.command.spi.ShellCommand;
import org.crsh.shell.ErrorType;
import org.crsh.text.RenderPrintWriter;
import org.crsh.util.Utils;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;

/** @author Julien Viet */
public class GroovyScriptShellCommand<T extends GroovyScriptCommand> extends ShellCommand<InvocationContext<T>> {

  /** . */
  private final Class<T> clazz;

  /** . */
  private final CommandDescriptor<InvocationContext<T>> descriptor;

  public GroovyScriptShellCommand(Class<T> clazz) {

    //
    CommandFactory factory = new CommandFactory(getClass().getClassLoader());

    //
    this.clazz = clazz;
    this.descriptor = HelpDescriptor.create(factory.create(clazz));
  }

  @Override
  public CommandDescriptor<InvocationContext<T>> getDescriptor() {
    return descriptor;
  }

  @Override
  protected Command<?, ?> resolveCommand(final InvocationMatch<InvocationContext<T>> match) {
    return new Command<Void, Object>() {
      @Override
      public CommandInvoker<Void, Object> getInvoker() throws CommandCreationException {
        List<String> chunks = Utils.chunks(match.getRest());
        String[] args = chunks.toArray(new String[chunks.size()]);
        return GroovyScriptShellCommand.this.getInvoker(args);
      }

      @Override
      public InvocationMatch<?> getMatch() {
        return match;
      }

      @Override
      public Class<Object> getProducedType() {
        return Object.class;
      }

      @Override
      public Class<Void> getConsumedType() {
        return Void.class;
      }
    };
  }

  private T createCommand() throws CommandCreationException {
    T command;
    try {
      command = clazz.newInstance();
    }
    catch (Exception e) {
      String name = clazz.getSimpleName();
      throw new CommandCreationException(name, ErrorType.INTERNAL, "Could not create command " + name + " instance", e);
    }
    return command;
  }

  @Override
  protected Completer getCompleter(RuntimeContext context) throws CommandCreationException {
    return null;
  }

  private CommandInvoker<Void, Object> getInvoker(final String[] args) throws CommandCreationException {
    final T command = createCommand();
    return new CommandInvoker<Void, Object>() {

      public final Class<Object> getProducedType() {
        return Object.class;
      }

      public final Class<Void> getConsumedType() {
        return Void.class;
      }

      public void open(CommandContext<? super Object> consumer) {
        // Set up current binding
        Binding binding = new Binding(consumer.getSession());

        // Set the args on the script
        binding.setProperty("args", args);

        //
        command.setBinding(binding);

        //
        command.pushContext(new InvocationContextImpl<Object>((CommandContext<Object>)consumer));

        //
        try {
          //
          Object res = command.run();

          // Evaluate the closure
          if (res instanceof Closure) {
            Closure closure = (Closure)res;
            res = closure.call(args);
          }

          //
          if (res != null) {
            RenderPrintWriter writer = command.peekContext().getWriter();
            if (writer.isEmpty()) {
              writer.print(res);
            }
          }
        }
        catch (Exception t) {
          throw GroovyCommand.unwrap(t);
        }
      }

      public void provide(Void element) throws IOException {
        // Should never be called
      }

      public void flush() throws IOException {
        command.peekContext().flush();
      }

      public void close() throws IOException, UndeclaredThrowableException {
        command.popContext();
      }
    };
  }


}
