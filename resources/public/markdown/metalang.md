# Manipula Meta Language

The Manipula Meta Language allows the specification of a sequence of actions to be performed. Actions can be grouped, nested, and named, allowing for reusable, complex action sequences. 

## Actions

### Open

The `open` action type signifies opening a URL. It must contain the keys `:type`, `:name`, and `:url`.

```clj
{:type :open,
 :name "Open stargate",
 :url "https://stargate.finance/transfer"}
```

### Group

The `group` action type groups multiple actions together. It must contain the keys `:type`, `:name`, and `:actions`.

```clj
{:type :group,
 :name "Stargate actions",
 :actions
 [{:type :open,
   :name "Open stargate webpage",
   :url "https://stargate.finance/transfer"}]}
```

### Mouse-move

The `mouse-move` action type signifies moving the mouse cursor to a specific coordinate. It must contain the keys `:type`, `:name`, and `:xy`.

```clj
{:type :mouse-move,
 :name "Move mouse to coordinate",
 :xy [1252 118]}
```

### Click

The `click` action type signifies a mouse click at a specific coordinate. It must contain the keys `:type`, `:name`, and `:xy`.

```clj
{:type :click,
 :name "Click at coordinate",
 :xy [1252 118]}
```

### Keyboard

The `keyboard` action type signifies keyboard input. It must contain the keys `:type`, `:name`, and `:keys`. Optionally, it can include `:delay`.

```clj
{:type :keyboard,
 :name "Type keys",
 :keys [:shift :a :b :c],
 :delay 1000}
```

## Checks

### Opened!

The `opened!` check type signifies that a specific URL is opened. It must contain the keys `:type` and `:url`.

```clj
{:type :opened!,
 :url "https://stargate.finance/transfer"}
```

### Pattern

The `pattern` check type is used for regex matching. It must contain the keys `:type`, `:xy`, `:length`, `:regexp`, and `:direction`. Optionally, it can include `:await`.

```clj
{:type :pattern,
 :xy [1494 555],
 :regexp "x{10}.{1,3}b{35,45}.{1,3}x{10}",
 :direction :ver,
 :length 100,
 :await 3000}
```
